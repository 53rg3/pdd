package core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import core.CommandConfig.CustomCommand;
import core.CommandConfig.Module;
import org.junit.Test;

import java.util.List;

public class CommandConfigTest {

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @Test
    public void sadf() {
        final CommandConfig config = new CommandConfig();

        config.modules.add(new Module(
                "httpstat",
                "/target/universal/",
                "/target/universal/",
                "0.1",
                List.of(9500)
        ));

        config.customCommands.add(new CustomCommand("es", "./elasticsearch"));

        System.out.println(this.gson.toJson(config, CommandConfig.class));
    }

}
