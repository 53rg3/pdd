package run;

import core.CommandConfig;
import core.CommandRunner;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.concurrent.Callable;

@Command(description = "\n" +
        "Helpers for developing microservices with Play. Deploy Play modules locally with single commands or " +
        "run your own predefined Linux shell commands.\n\n" +
        "Options:",
        name = "java pdd.jar", mixinStandardHelpOptions = true, version = "Play Dev Deploy 0.1")
public class PDD implements Callable<Integer> {

    // todo
    //  - `add command` & `add module` (i.e. CLI command to add new stuff to config, to avoid mistakes)
    //  - maybe gnome-terminal configurable? There's also konsole and xterm. gnome-terminal has no option for '--noclose'

    private final CommandConfig commandConfig = CommandConfig.load();
    public static final String DUMMY_VALUE = "!?+DUMMY_VALUE!?+";

    @Parameters(index = "0",
            description = "= name of command (run, multirun, deploy, compile, print) or your custom command")
    private String commandName;

    @Parameters(index = "1",
            description = "= name of the Play module (defined in config.json). No use if custom command.",
            defaultValue = DUMMY_VALUE)
    private String moduleName;

    public static void main(final String[] args) {
        final int exitCode = new CommandLine(new PDD()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        return CommandRunner.run(this.commandName, this.moduleName, this.commandConfig);
    }
}
