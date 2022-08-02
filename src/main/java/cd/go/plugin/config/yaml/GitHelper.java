package cd.go.plugin.config.yaml;

import com.thoughtworks.go.plugin.api.logging.Logger;

import java.io.*;

public class GitHelper {

    private static Logger LOGGER = Logger.getLoggerFor(GitHelper.class);
    private final String repoUrl;
    private final String branch;
    private final String basePath;
    private final File workingDir;

    public static final String WORKING_DIR_BASE = "/godata/gocd_yaml_config_plugin_template_repos";

    public GitHelper(String repoUrl, String branch, String basePath, File workingDir) {
        this.repoUrl = repoUrl;
        this.branch = branch;
        this.workingDir = workingDir;
        this.basePath = basePath;
        initializeRepo();
    }

    public String getWorkingDirAbsolutPath() {
        return workingDir.getAbsolutePath();
    }

    public String getRepoUrl() {
        return repoUrl;
    }

    public String getBranch() {
        return branch;
    }

    public String getBasePath() {
        return basePath;
    }

    public File getWorkingDir() {
        return workingDir;
    }

    public static String generateSanitizedRepoName(String repo) {
        return repo.substring(repo.lastIndexOf(":") + 1).replace("/", "_");
    }

    void initializeRepo()  {
        File gitfile = new File(workingDir.getAbsolutePath() + "/" + ".git");
        if (! gitfile.exists()) {
            LOGGER.info("initializeRepo(): Cloning {} into {}", repoUrl, workingDir.getAbsolutePath());
            runCommand("git clone " + repoUrl + " " + workingDir.getAbsolutePath());
            LOGGER.info("initializeRepo(): Checking out branch {} into {}", branch, workingDir.getAbsolutePath());
            runCommand("git checkout " + branch, workingDir);
        } else {
            LOGGER.info("initializeRepo(): Repo {} exists locally, calling refreshRepo() instead.", repoUrl);
            refreshRepo();
        }
    }

    public void refreshRepo() {
        LOGGER.info("refreshRepo(): Running git pull for repoUrl {} and branch {} into {}", repoUrl, branch, workingDir.getAbsolutePath());
        runCommand("git pull", workingDir);
    }
    
    private boolean runCommand(String command) {
        return runCommand(command, null);
    }
    
    private boolean runCommand(String command, File dir) {
        try {
            Process process = Runtime.getRuntime().exec(command, null, dir);
            process.waitFor();
            
            int processResult = process.exitValue();
            if (processResult != 0) {
                logStdErr(process);
               return false;
            }
            logStdOut(process);
            return true;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void logStdOut(Process process) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println(line);
        }
        reader.close();
    }

    private void logStdErr(Process process) throws IOException {
        BufferedReader stdErr = new BufferedReader(new InputStreamReader(process.getErrorStream()));
        String line;
        while ((line = stdErr.readLine()) != null) {
            System.out.println(line);
        }
        stdErr.close();
    }


    public static void main(String[] args) {
        GitHelper gitHelper = new GitHelper("ssh://git@bitbucket.org/openalpr/cloudops-gocd-library.git", "develop", "templates/", new File("test"));
    }
}