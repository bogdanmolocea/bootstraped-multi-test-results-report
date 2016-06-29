package com.github.bogdanlivadariu.jenkins.reporting.testng;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.slaves.SlaveComputer;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;

import org.apache.tools.ant.DirectoryScanner;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.github.bogdanlivadariu.reporting.testng.builder.TestNgReportBuilder;

@SuppressWarnings("unchecked")
public class TestNGTestReportPublisher extends Recorder {

    private static final String DEFAULT_FILE_INCLUDE_PATTERN = "**/*.xml";

    private final String jsonReportDirectory;

    private final String fileIncludePattern;

    private final String fileExcludePattern;

    private final boolean markAsUnstable;

    private final boolean copyHTMLInWorkspace;

    @DataBoundConstructor
    public TestNGTestReportPublisher(String jsonReportDirectory, String fileIncludePattern, String fileExcludePattern,
        boolean markAsUnstable, boolean copyHTMLInWorkspace) {
        this.jsonReportDirectory = jsonReportDirectory;
        this.fileIncludePattern = fileIncludePattern;
        this.fileExcludePattern = fileExcludePattern;
        this.markAsUnstable = markAsUnstable;
        this.copyHTMLInWorkspace = copyHTMLInWorkspace;
    }

    public String getJsonReportDirectory() {
        return jsonReportDirectory;
    }

    public String getFileIncludePattern() {
        return fileIncludePattern;
    }

    public String getFileExcludePattern() {
        return fileExcludePattern;
    }

    public boolean isMarkAsUnstable() {
        return markAsUnstable;
    }

    public boolean isCopyHTMLInWorkspace() {
        return copyHTMLInWorkspace;
    }

    private String[] findJsonFiles(File targetDirectory, String fileIncludePattern, String fileExcludePattern) {
        DirectoryScanner scanner = new DirectoryScanner();
        if (fileIncludePattern == null || fileIncludePattern.isEmpty()) {
            scanner.setIncludes(new String[] {DEFAULT_FILE_INCLUDE_PATTERN});
        } else {
            scanner.setIncludes(new String[] {fileIncludePattern});
        }
        if (fileExcludePattern != null) {
            scanner.setExcludes(new String[] {fileExcludePattern});
        }
        scanner.setBasedir(targetDirectory);
        scanner.scan();
        return scanner.getIncludedFiles();
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener)
        throws IOException, InterruptedException {

        listener.getLogger().println("[TestNGReportPublisher] Compiling TestNG Html Reports ...");

        // source directory (possibly on slave)
        FilePath workspaceJsonReportDirectory;
        if (getJsonReportDirectory().isEmpty()) {
            workspaceJsonReportDirectory = build.getWorkspace();
        } else {
            workspaceJsonReportDirectory = new FilePath(build.getWorkspace(), getJsonReportDirectory());
        }

        // target directory (always on master)
        File targetBuildDirectory = new File(build.getRootDir(), "testng-reports-with-handlebars");
        if (!targetBuildDirectory.exists()) {
            targetBuildDirectory.mkdirs();
        }

        if (Computer.currentComputer() instanceof SlaveComputer) {
            listener.getLogger().println(
                "[TestNG test report builder] Copying all xml files from slave: "
                    + workspaceJsonReportDirectory.getRemote() + " to master reports directory: "
                    + targetBuildDirectory);
        } else {
            listener.getLogger().println(
                "[TestNG test report builder] Copying all xml files from: "
                    + workspaceJsonReportDirectory.getRemote()
                    + " to reports directory: " + targetBuildDirectory);
        }
        File targetBuildJsonDirectory = new File(targetBuildDirectory.getAbsolutePath() + "/xmlData");
        workspaceJsonReportDirectory.copyRecursiveTo(DEFAULT_FILE_INCLUDE_PATTERN, new FilePath(
            targetBuildJsonDirectory));

        // generate the reports from the targetBuildDirectory
        Result result = Result.NOT_BUILT;
        String[] jsonReportFiles =
            findJsonFiles(targetBuildJsonDirectory, getFileIncludePattern(), getFileExcludePattern());
        if (jsonReportFiles.length > 0) {
            listener.getLogger().println(
                String.format("[TestNGReportPublisher] Found %d xml files.", jsonReportFiles.length));
            int jsonIndex = 0;
            for (String jsonReportFile : jsonReportFiles) {
                listener.getLogger().println(
                    "[TestNG test report builder] " + jsonIndex + ". Found a xml file: " + jsonReportFile);
                jsonIndex++;
            }
            listener.getLogger().println("[TestNG test report builder] Generating HTML reports");

            try {
                List<String> fullJsonPaths = new ArrayList<String>();
                // reportBuilder.generateReports();
                for (String fi : jsonReportFiles) {
                    fullJsonPaths.add(targetBuildJsonDirectory + "/" + fi);
                }
                for (String ss : fullPathToXmlFiles(jsonReportFiles, targetBuildJsonDirectory)) {
                    listener.getLogger().println("processing: " + ss);
                }
                TestNgReportBuilder rep =
                    new TestNgReportBuilder(fullPathToXmlFiles(jsonReportFiles, targetBuildJsonDirectory),
                        targetBuildDirectory.getAbsolutePath());

                boolean featuresResult = rep.writeReportsOnDisk();
                if (featuresResult) {
                    result = Result.SUCCESS;
                } else {
                    result = isMarkAsUnstable() ? Result.UNSTABLE : Result.FAILURE;
                }

                // finally copy to workspace, if needed
                if (isCopyHTMLInWorkspace()) {
                    FilePath workspaceCopyDirectory = new FilePath(build.getWorkspace(), "testng-reports-with-handlebars");
                    if (workspaceCopyDirectory.exists()) {
                        workspaceCopyDirectory.deleteRecursive();
                    }
                    listener.getLogger().println(
                        "[TestNG test report builder] Copying report to workspace directory: " + workspaceCopyDirectory.toURI());
                    new FilePath(targetBuildDirectory).copyRecursiveTo("**/*.html", workspaceCopyDirectory);
                }

            } catch (Exception e) {
                result = Result.FAILURE;
                listener.getLogger().println(
                    "[TestNG test report builder] there was an error generating the reports: " + e);
                for (StackTraceElement error : e.getStackTrace()) {
                    listener.getLogger().println(error);
                }
            }
        } else {
            result = Result.SUCCESS;
            listener.getLogger().println(
                "[TestNG test report builder] xml path for the reports might be wrong, " + targetBuildDirectory);
        }

        build.addAction(new TestNGTestReportBuildAction(build));
        build.setResult(result);

        return true;
    }

    private List<String> fullPathToXmlFiles(String[] xmlFiles, File targetBuildDirectory) {
        List<String> fullPathList = new ArrayList<String>();
        for (String file : xmlFiles) {
            fullPathList.add(new File(targetBuildDirectory, file).getAbsolutePath());
        }
        return fullPathList;
    }

    @Override
    public Action getProjectAction(AbstractProject< ? , ? > project) {
        return new TestNGTestReportProjectAction(project);
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        @Override
        public String getDisplayName() {
            return "Publish TestNG reports generated with handlebars";
        }

        // Performs on-the-fly validation on the file mask wildcard.
        public FormValidation doCheck(@AncestorInPath AbstractProject project,
            @QueryParameter String value) throws IOException, ServletException {
            FilePath ws = project.getSomeWorkspace();
            return ws != null ? ws.validateRelativeDirectory(value) : FormValidation.ok();
        }

        @Override
        public boolean isApplicable(Class< ? extends AbstractProject> jobType) {
            return true;
        }
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }
}
