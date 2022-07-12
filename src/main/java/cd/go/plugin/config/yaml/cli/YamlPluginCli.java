package cd.go.plugin.config.yaml.cli;

import cd.go.plugin.config.yaml.GitHelper;
import com.thoughtworks.go.plugin.api.logging.Logger;
import cd.go.plugin.config.yaml.JsonConfigCollection;
import cd.go.plugin.config.yaml.YamlConfigParser;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import com.google.gson.JsonObject;

import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

import static java.lang.String.format;

public class YamlPluginCli {
    private static Logger LOGGER = Logger.getLoggerFor(YamlPluginCli.class);

    public static void main(String[] args) {
        RootCmd root = new RootCmd();
        SyntaxCmd syntax = new SyntaxCmd();

        JCommander cmd = JCommander.newBuilder().
                programName("yaml-cli").
                addObject(root).
                addCommand("syntax", syntax).
                build();

        try {
            cmd.parse(args);

            if (root.help) {
                printUsageAndExit(0, cmd, cmd.getParsedCommand());
            }

            if (syntax.help) {
                printUsageAndExit(0, cmd, cmd.getParsedCommand());
            }

            if (null == syntax.file) {
                printUsageAndExit(1, cmd, cmd.getParsedCommand());
            }
        } catch (ParameterException e) {
            error(e.getMessage());
            printUsageAndExit(1, cmd, cmd.getParsedCommand());
        }
        try {
            File workingDir = Files.createTempDirectory("templateRepo").toFile();
            GitHelper gitHelper = new GitHelper("templateRepo", "develop", "templateBasePath", workingDir);

            YamlConfigParser parser = new YamlConfigParser(gitHelper);
            JsonConfigCollection collection = new JsonConfigCollection();
            parser.parseStream(collection, getFileAsStream(syntax.file), getLocation(syntax.file));

            if (collection.getErrors().size() > 0) {
                JsonObject result = collection.getJsonObject();
                result.remove("environments");
                result.remove("pipelines");
                result.addProperty("valid", false);
                die(1, result.toString());
            } else {
                die(0, "{\"valid\":true}");
            }
    
        } catch (Exception e) {
            LOGGER.error("Error while trying to initialize template repo \n Message: {} \n StackTrace: {}", e.getMessage(), e.getStackTrace(), e);
            throw new RuntimeException(e);
        }


    }

    private static String getLocation(String file) {
        return "-".equals(file) ? "<STDIN>" : file;
    }

    private static InputStream getFileAsStream(String file) {
        InputStream s = null;
        try {
            s = "-".equals(file) ? System.in : new FileInputStream(new File(file));
        } catch (FileNotFoundException e) {
            die(1, e.getMessage());
        }
        return s;
    }

    private static void echo(String message, Object... items) {
        System.out.println(format(message, items));
    }

    private static void error(String message, Object... items) {
        System.err.println(format(message, items));
    }

    private static void die(int exitCode, String message, Object... items) {
        if (exitCode != 0) {
            error(message, items);
        } else {
            echo(message, items);
        }
        System.exit(exitCode);
    }

    private static void printUsageAndExit(int exitCode, JCommander cmd, String command) {
        StringBuilder out = new StringBuilder();
        if (null == command) {
            cmd.getUsageFormatter().usage(out);
        } else {
            cmd.getUsageFormatter().usage(command, out);
        }
        die(exitCode, out.toString());
    }
}
