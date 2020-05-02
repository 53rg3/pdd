package core;

import core.CommandConfig.CustomCommand;
import core.CommandConfig.Module;
import core.CommandConfig.ModuleCommand;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static core.CommandConfig.ModuleCommand.print;
import static core.Utils.exitWithError;
import static java.lang.System.out;
import static run.PDD.DUMMY_VALUE;

public class CommandRunner {

    public static Integer run(final String param0, final String param1, final CommandConfig commandConfig) {
        try {
            // This will throw if param0 is not a ModuleCommand, i.e. it's a CustomCommand
            if (param0.equals(print.name())) {
                return printCommandConfig(commandConfig);
            }
            return runModuleCommand(ModuleCommand.valueOf(param0), param1, commandConfig);
        } catch (final Exception e) {
            return runCustomCommand(param0, commandConfig);
        }
    }

    // ------------------------------------------------------------------------------------------ //
    // PRIVATE
    // ------------------------------------------------------------------------------------------ //

    private static Integer execute(final String shellCommandAsString, final CommandConfig commandConfig) {

        try {
            final Process process = new ProcessBuilder(commandConfig.shell, "-c", shellCommandAsString).start();
            new BufferedReader(new InputStreamReader(process.getInputStream())).lines().forEach(System.out::println);
            new BufferedReader(new InputStreamReader(process.getErrorStream())).lines().forEach(System.out::println);
            final int exitCode = process.waitFor();
            if (exitCode != 0) {
                exitWithError("Failed to execute command: " + shellCommandAsString);
            }
            return exitCode;
        } catch (final IOException e) {
            exitWithError("" +
                    "Couldn't execute: " + shellCommandAsString + "\n" +
                    "Cause: " + e.getMessage());
        } catch (final InterruptedException e) {
            exitWithError("" +
                    "Process interrupted.\n" +
                    "Cause: " + e.getMessage());
        }
        throw new IllegalStateException("If you see this, then it's a bug.");
    }

    private static Integer runCustomCommand(final String commandName, final CommandConfig commandConfig) {
        final Optional<CustomCommand> customCommand = commandConfig.getCustomCommand(commandName);
        if (customCommand.isEmpty()) {
            exitWithError("Requested custom command does not exist in config.json, need: '" + commandName + "'.");
        }

        return execute(customCommand.get().command, commandConfig);
    }

    private static Integer runModuleCommand(final ModuleCommand moduleCommand,
                                            final String moduleName,
                                            final CommandConfig commandConfig) {

        if (moduleName.equals(DUMMY_VALUE)) {
            exitWithError("No module specified as 2nd parameter.");
        }

        final Optional<Module> optional = commandConfig.getModule(moduleName);
        if (optional.isEmpty()) {
            exitWithError("Requested module does not exist in config.json, need: '" + moduleName + "'.");
        }
        final Module module = optional.get();


        switch (moduleCommand) {
            case run:
                return runSingle(module, commandConfig);

            case multirun:
                return runMulti(module, commandConfig);

            case deploy:
                return deploy(module, commandConfig);

            case compile:
                return compile(commandConfig, module);

            default:
                exitWithError("Command not implemented: " + moduleCommand);
        }

        throw new IllegalStateException("If you see this, then it's a bug.");
    }

    private static Integer compile(final CommandConfig commandConfig, final Module module) {
        return execute("(cd " + commandConfig.projectRoot + " && exec sbt " + module.name + "/dist)", commandConfig);
    }

    private static Integer printCommandConfig(final CommandConfig commandConfig) {
        try {
            out.println("Settings in your config.json:");
            out.println("\tProject Root: " + commandConfig.projectRoot);
            out.println("\tShell: " + commandConfig.shell);
            out.println("\tModules:");
            commandConfig.modules.forEach(module -> out.println("\t\t" + module.name + ": " + Arrays.toString(module.ports.toArray())));
            out.println("\tCustom Commands:");
            commandConfig.customCommands.forEach(command -> out.println("\t\t" + command.name + ": " + command.command));
        } catch (final Exception e) {
            exitWithError("Failed. Cause: " + e.getMessage());
            return 1;
        }
        return 0;
    }

    private static Integer deploy(final Module module, final CommandConfig commandConfig) {

        // Old Play folder
        final Path directoryPath = getPlayDirectoryPath(module);
        final boolean doesOldPlayFolderExist = directoryPath.toFile().isDirectory();

        // Compiled zip file
        final String zipFileName = module.name + "-" + module.version + ".zip";
        final Path expectedZipFile = Paths.get(module.zipShallBeExtractedToFolder).resolve(zipFileName);
        final boolean doesZipExist = expectedZipFile.toFile().exists();

        if (doesZipExist) {
            if (doesOldPlayFolderExist) {
                out.println("+ deleting " + directoryPath);
                deletePlayFolder(directoryPath, commandConfig);
            }

            out.println("+ unzipping " + expectedZipFile);
            execute("unzip " + expectedZipFile + " -d " + module.zipShallBeExtractedToFolder, commandConfig);

            out.println("+ trying to run Play");
            return runPlay(module, module.ports.get(0), directoryPath, commandConfig);
        } else {
            exitWithError("Can't deploy '" + module.name + "' because compiled zip. Run compile first.");
        }

        return 1;
    }

    private static Integer runSingle(final Module module, final CommandConfig commandConfig) {
        final Path directoryPath = getPlayDirectoryPath(module);

        exitIfPlayDirectoryDoesNotExist(directoryPath);

        deleteRunningPidFile(directoryPath);

        return execute(createRunPlayCommand(module, module.ports.get(0), directoryPath), commandConfig);
    }

    private static Integer runMulti(final Module module, final CommandConfig commandConfig) {
        final Path directoryPath = getPlayDirectoryPath(module);

        exitIfPlayDirectoryDoesNotExist(directoryPath);

        deleteRunningPidFile(directoryPath);

        Integer lastExitCode = 1;
        for (final Integer port : module.ports) {
            out.println("+ trying to run on port: " + port);
            lastExitCode = execute(createRunPlayCommand(module, port, directoryPath), commandConfig);
            sleep(1000);
            deleteRunningPidFile(directoryPath);
            if (lastExitCode != 0) {
                exitWithError("Failed to run Play at port: " + port);
            }
        }

        return lastExitCode;
    }

    private static void exitIfPlayDirectoryDoesNotExist(final Path directoryPath) {
        if (!directoryPath.toFile().isDirectory()) {
            exitWithError("Can't find expected Play directory to run (is version in config.json correct?): " + directoryPath);
        }
    }

    private static Path getPlayDirectoryPath(final Module module) {
        final String unzippedDirectoryName = module.name + "-" + module.version;
        return Paths.get(module.zipShallBeExtractedToFolder).resolve(unzippedDirectoryName);
    }

    private static void deleteRunningPidFile(final Path directoryPath) {
        final File pidFile = directoryPath.resolve("RUNNING_PID").toFile();
        if (pidFile.exists() && !pidFile.delete()) {
            exitWithError("RUNNING_PID file exists, but couldn't be deleted.");
        }
    }

    private static String createRunPlayCommand(final Module module, final Integer port, final Path directoryPath) {
        return "gnome-terminal -e \"" + directoryPath + "/bin/" + module.name + " " +
                "-Dplay.http.secret.key='" + UUID.randomUUID().toString() + "' " +
                "-Dhttp.port=" + port + "\"";
    }

    private static Integer runPlay(final Module module, final Integer port, final Path directoryPath, final CommandConfig commandConfig) {

        deleteRunningPidFile(directoryPath);

        return execute(createRunPlayCommand(module, port, directoryPath), commandConfig);
    }

    private static void deletePlayFolder(final Path directoryPath, final CommandConfig commandConfig) {
        if (directoryPath.toFile().isDirectory()) {
            if (execute("rm -r " + directoryPath, commandConfig) != 0) {
                exitWithError("Deleting " + directoryPath + " failed. See terminal output.");
            }
        } else {
            exitWithError(directoryPath + " is not directory.");
        }
    }

    private static void sleep(final int ms) {
        try {
            Thread.sleep(ms);
        } catch (final InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

}
