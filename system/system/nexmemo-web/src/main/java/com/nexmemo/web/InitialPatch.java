package com.nexmemo.web;

import com.nexmemo.core.constant.CurrentDbServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.File;

/**
 * @author : Rohit Chapagain
 * @created : 2026-05-10, Sunday
 **/
@Slf4j
@Component
@RequiredArgsConstructor
public class InitialPatch implements ApplicationRunner {

    private static final String GENERAL_PATCH_FOLDER = "general_patch";
    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        try {
            // Diagnostic: Find all SQL files in the project
            findSqlFiles();

            // Get database connection info
            String jdbcUrl = dataSource.getConnection().getMetaData().getURL();
            String dbFolder = CurrentDbServer.currentConnectedDb(jdbcUrl);

            // Remove leading slash if present
            if (dbFolder.startsWith("/")) {
                dbFolder = dbFolder.substring(1);
            }

            log.info("Running initial patch for DB: {}", dbFolder);
            runPatch(dbFolder);
            log.info("Initial patch completed successfully.");

        } catch (Exception e) {
            // Log but do NOT crash the app — patch failures shouldn't stop startup
            log.error("Initial patch failed: {}", e.getMessage(), e);
        }
    }

    private void runPatch(String baseServerFolder) {
        // Build paths without leading slashes
        String locationPath = baseServerFolder + "/" + GENERAL_PATCH_FOLDER + "/location.sql";
        String patchPath = baseServerFolder + "/" + GENERAL_PATCH_FOLDER + "/patch.sql";
        String workflowPatchPath = baseServerFolder + "/" + GENERAL_PATCH_FOLDER + "/patch-workflow-entities.sql";

        log.debug("Looking for location.sql at: {}", locationPath);
        log.debug("Looking for patch.sql at: {}", patchPath);
        log.debug("Looking for patch-workflow-entities.sql at: {}", workflowPatchPath);

        runSqlFile(locationPath);
        runSqlFile(patchPath);
        runSqlFile(workflowPatchPath);
    }

    private void runSqlFile(String classpathPath) {
        try {
            // Try multiple ways to find the file
            ClassPathResource resource = new ClassPathResource(classpathPath);

            if (!resource.exists()) {
                log.warn("Not found with standard path: {}", classpathPath);

                // Try alternative paths
                boolean found = tryAlternativePaths(classpathPath);
                if (!found) {
                    log.warn("Patch file not found on classpath: {} — skipping", classpathPath);
                    return;
                }
                // If found in alternatives, resource would be updated, but we need to reload
                resource = new ClassPathResource(classpathPath); // Reload after potential copy
                if (!resource.exists()) {
                    // Try the manual copy approach as last resort
                    if (copySqlFileFromSource(classpathPath)) {
                        resource = new ClassPathResource(classpathPath);
                    } else {
                        log.warn("Unable to locate patch file: {} — skipping", classpathPath);
                        return;
                    }
                }
            }

            log.info("Found patch file, executing: {}", classpathPath);
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator(resource);
            populator.setSeparator("GO");           // SQL Server uses GO as statement separator
            populator.setContinueOnError(false);    // stop on first error
            populator.execute(dataSource);
            log.info("Successfully executed patch: {}", classpathPath);
        } catch (Exception e) {
            log.error("Failed to execute patch {}: {}", classpathPath, e.getMessage());
            throw new RuntimeException("Patch execution failed: " + classpathPath, e);
        }
    }

    private boolean tryAlternativePaths(String originalPath) {
        String justFileName = originalPath.substring(originalPath.lastIndexOf("/") + 1);
        String[] possiblePaths = {
                originalPath,
                "/" + originalPath,
                "patch_ms_sql/general_patch/" + justFileName,
                "/patch_ms_sql/general_patch/" + justFileName,
                "classpath:" + originalPath,
                "src/main/resources/" + originalPath
        };

        for (String path : possiblePaths) {
            try {
                ClassPathResource tryResource = new ClassPathResource(path);
                if (tryResource.exists()) {
                    log.info("Found resource at alternative path: {}", path);
                    return true;
                }
            } catch (Exception e) {
                // Continue trying
            }
        }
        return false;
    }

    private boolean copySqlFileFromSource(String filename) {
        try {
            // Get just the filename from the path
            String justFileName = filename.substring(filename.lastIndexOf("/") + 1);

            // Look for the file in source directory
            String sourcePath = "src/main/resources/patch_ms_sql/general_patch/" + justFileName;
            File sourceFile = new File(sourcePath);

            if (sourceFile.exists()) {
                log.info("Found file in source directory: {}", sourcePath);
                log.info("Please manually copy this file to: target/classes/patch_ms_sql/general_patch/");
                log.info("Or rebuild your project with: mvn clean compile");
                return false;
            } else {
                log.warn("Source file not found at: {}", sourcePath);
                return false;
            }
        } catch (Exception e) {
            log.error("Error checking source file: {}", e.getMessage());
            return false;
        }
    }

    private void findSqlFiles() {
        log.info("=== Searching for SQL files ===");

        // Search from project root
        File projectRoot = new File(".");
        log.info("Project root: {}", projectRoot.getAbsolutePath());

        // Search for SQL files in the project
        findFile(projectRoot, "location.sql");
        findFile(projectRoot, "patch.sql");
        findFile(projectRoot, "patch-workflow-entities.sql");

        // Also search using classpath scanner
        try {
            ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath*:**/*.sql");
            log.info("SQL files found on classpath: {}", resources.length);
            for (Resource resource : resources) {
                log.info("  - {}", resource.getURL());
            }
        } catch (Exception e) {
            log.error("Error scanning classpath for SQL files: {}", e.getMessage());
        }

        log.info("=== End of search ===");
    }

    private void findFile(File directory, String fileName) {
        if (directory == null || !directory.exists()) return;

        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    // Skip some directories to speed up search
                    if (!file.getName().equals("target") &&
                            !file.getName().equals(".git") &&
                            !file.getName().equals(".idea")) {
                        findFile(file, fileName);
                    }
                } else if (file.getName().equals(fileName)) {
                    log.info("Found {} at: {}", fileName, file.getAbsolutePath());
                }
            }
        }
    }
}
