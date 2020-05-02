package core;

import com.google.gson.Gson;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static core.Utils.exitWithError;

public class CommandConfig {

    String projectRoot;
    String shell;
    final List<Module> modules = new ArrayList<>();
    final List<CustomCommand> customCommands = new ArrayList<>();


    // ------------------------------------------------------------------------------------------ //
    // PUBLIC
    // ------------------------------------------------------------------------------------------ //

    public Optional<Module> getModule(final String moduleName) {
        for (final Module module : this.modules) {
            if (module.name.equals(moduleName)) {
                return Optional.of(module);
            }
        }
        return Optional.empty();
    }

    public Optional<CustomCommand> getCustomCommand(final String commandName) {
        for (final CustomCommand customCommand : this.customCommands) {
            if (customCommand.name.equals(commandName)) {
                return Optional.of(customCommand);
            }
        }
        return Optional.empty();
    }

    public static CommandConfig load() {
        final Path path = Paths.get("config.json");

        // CHECK IF EXISTS
        if (!path.toFile().exists()) {
            exitWithError("" +
                    "Failed to find config.json at: " + path.toAbsolutePath());
        }

        // TRY TO LOAD AS STRING
        String json = null;
        try {
            json = new String(Files.readAllBytes(path));
        } catch (final IOException e) {
            exitWithError("" +
                    "Failed to load config.json from: " + path.toAbsolutePath() + "\n" +
                    "Cause: " + e.getMessage());
        }

        // PARSE JSON STRING TO OBJECT
        CommandConfig commandConfig = new CommandConfig();
        try {
            commandConfig = new Gson().fromJson(json, CommandConfig.class);
        } catch (final Exception e) {
            exitWithError("" +
                    "Failed to parse config.json from: " + path.toAbsolutePath() + "\n" +
                    "Cause: " + e.getMessage());
        }

        // ENSURE JSON WAS NOT EMPTY
        if (commandConfig == null) {
            exitWithError("Failed to parse config.json from: " + path.toAbsolutePath());
        }

        // ENSURE AT LEAST ONE COMMAND EXISTS
        assert commandConfig != null;
        if (commandConfig.modules.isEmpty() && commandConfig.customCommands.isEmpty()) {
            exitWithError("No modules or custom commands configured in config.json");
        }

        // ENSURE NO RESERVED WORDS ARE USED
        commandConfig.customCommands.forEach(CommandConfig::validate);

        // ENSURE CUSTOM COMMAND HAS ALL FIELDS
        if (!commandConfig.customCommands.isEmpty()) {
            for (final CustomCommand customCommand : commandConfig.customCommands) {
                failIfCommandIsNullOrEmpty(customCommand.name, "name", "custom command");
                failIfCommandIsNullOrEmpty(customCommand.command, "command", "custom command");
            }
        }


        // ENSURE CUSTOM COMMAND HAS ALL FIELDS
        if (!commandConfig.modules.isEmpty()) {
            for (final Module module : commandConfig.modules) {
                failIfCommandIsNullOrEmpty(module.name, "name", "module");
                failIfCommandIsNullOrEmpty(module.version, "version", "module");

                failIfCommandIsNullOrEmpty(module.buildZipCanBeFoundInFolder, "buildZipCanBeFoundInFolder", "module");
                failIfNotDirectory(module.buildZipCanBeFoundInFolder, "buildZipCanBeFoundInFolder");

                failIfCommandIsNullOrEmpty(module.zipShallBeExtractedToFolder, "zipShallBeExtractedToFolder", "module");
                failIfNotDirectory(module.zipShallBeExtractedToFolder, "zipShallBeExtractedToFolder");

                failIfPortsIsNullOrEmpty(module.ports);
            }
        }

        // ENSURE PROJECT ROOT IS OK
        if (commandConfig.projectRoot == null || commandConfig.projectRoot.equals("")) {
            exitWithError("Field 'projectRoot' in config.json does not exist or is empty, " +
                    "value: " + commandConfig.projectRoot);
        }
        failIfNotDirectory(commandConfig.projectRoot, "projectRoot");

        // ENSURE SHELL IS OK
        if (commandConfig.shell == null || commandConfig.shell.equals("") || !new File(commandConfig.shell).exists()) {
            exitWithError("Field 'shell' in config.json does not exist, is empty or program doesn't exist, " +
                    "value: " + commandConfig.shell);
        }

        return commandConfig;
    }


    // ------------------------------------------------------------------------------------------ //
    // PRIVATE
    // ------------------------------------------------------------------------------------------ //

    private static void failIfPortsIsNullOrEmpty(final List<Integer> ports) {
        if (ports == null) {
            exitWithError("Field 'ports' for a module is not set");
        }
        if (ports.isEmpty()) {
            exitWithError("Field 'ports' for a module is empty");
        }
    }

    private static void failIfNotDirectory(final String path, final String fieldName) {
        final File file = new File(path);
        if (!file.exists() || !file.isDirectory()) {
            exitWithError("Directory for field '" + fieldName + "' is not a directory or does not exist. Path: " + path);
        }
    }

    private static void failIfCommandIsNullOrEmpty(final String value, final String fieldName, final String type) {
        if (value == null || value.equals("")) {
            exitWithError("Config.json contains " + type + " which has empty or no '" + fieldName + "' field.");
        }
    }

    private static void validate(final CustomCommand customCommand) {
        for (final ModuleCommand command : ModuleCommand.values()) {
            if (command.name().equals(customCommand.name)) {
                exitWithError("Custom commands must not use reserved words, change '" + customCommand.name + "'\n" +
                        "Reserved words: " + Arrays.toString(ModuleCommand.values()));
            }
        }
    }

// ------------------------------------------------------------------------------------------ //
// INNER CLASSES
// ------------------------------------------------------------------------------------------ //

    static class Module {
        String name;
        String buildZipCanBeFoundInFolder;
        String zipShallBeExtractedToFolder;
        String version;
        List<Integer> ports;

        Module(final String name,
               final String buildZipCanBeFoundInFolder,
               final String zipShallBeExtractedToFolder,
               final String version,
               final List<Integer> ports) {
            this.name = name;
            this.buildZipCanBeFoundInFolder = buildZipCanBeFoundInFolder;
            this.zipShallBeExtractedToFolder = zipShallBeExtractedToFolder;
            this.version = version;
            this.ports = ports;
        }
    }

    static class CustomCommand {
        String name;
        String command;

        CustomCommand(final String name, final String command) {
            this.name = name;
            this.command = command;
        }
    }

    enum ModuleCommand {
        run,
        multirun,
        deploy,
        compile,
        print;
    }

}
