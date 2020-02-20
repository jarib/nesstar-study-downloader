package no.jaribakken.nesstar;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.nesstar.api.Category;
import com.nesstar.api.DdiList;
import com.nesstar.api.FileFormat;
import com.nesstar.api.NesstarDB;
import com.nesstar.api.NesstarDBFactory;
import com.nesstar.api.NotAuthorizedException;
import com.nesstar.api.ResultStream;
import com.nesstar.api.Server;
import com.nesstar.api.Study;
import com.nesstar.api.Variable;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.rmi.ServerException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class NesstarStudyDownloader {
    private static final String META_PATH = "%s/%s-meta.json";
    private static final String DATA_PATH = "%s/%s-data.csv.zip";
    private final NesstarDB nesstarDB;
    private final Server server;
    private final String outputPath;
    private final Gson gson = new GsonBuilder().serializeNulls().create();
    private final SimpleDateFormat dateFormat =
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    public NesstarStudyDownloader(final URI serverURI, final String username, final String password,
                                  final String outPath) throws IOException {
        outputPath = outPath;
        nesstarDB = NesstarDBFactory.getInstance();
        server = nesstarDB.getServer(serverURI);

        if (username != null && password != null) {
            System.out.println(String.format("Logging in as %s", username));
            server.login(username, password);
        }
    }

    public void downloadAllStudies()
        throws NotAuthorizedException, IOException, InterruptedException {
        System.out.println("Fetching list of studies...");

        final List<Study> studies = server.getBank(Study.class).getAll();

        for (final Study study : studies) {
            if (alreadyDownloaded(study)) {
                System.out.println(
                    String.format("Already downloaded: %s - %s", study.getId(), study.getLabel()));
            } else {
                downloadStudy(study);
            }
        }
    }

    public void downloadStudyFor(final String key)
        throws NotAuthorizedException, IOException, InterruptedException {
        final Study study = server.getBank(Study.class).get(key);
        downloadStudy(study);
    }

    private boolean alreadyDownloaded(final Study study)
        throws NotAuthorizedException, IOException {
        final File metaFile = new File(getMetaPath(study));
        final File dataFile = new File(getDataPath(study));

        final Date lastModified = study.getTimeStamp();

        final boolean hasData =
            dataFile.exists() && new Date(dataFile.lastModified()).compareTo(lastModified) > 0;

        final boolean hasMeta =
            metaFile.exists() && new Date(metaFile.lastModified()).compareTo(lastModified) > 0;

        return hasData && hasMeta;
    }

    private String getDataPath(final Study study) {
        return String.format(DATA_PATH, outputPath, study.getId());
    }

    private String getMetaPath(final Study study) {
        return String.format(META_PATH, outputPath, study.getId());
    }

    private void downloadStudy(final Study study)
        throws IOException, NotAuthorizedException, InterruptedException {
        System.out.println(String.format("Downloading: %s - %s", study.getId(), study.getLabel()));

        try {
            writeData(study);
            writeMetadata(study);
        } catch (final IOException e) {
            final Throwable cause = e.getCause();

            if (cause instanceof ServerException) {
                System.out.println(String.format("Caught server error for %s", study.getId()));

                final ServerException err = (ServerException)cause;
                System.out.println(err.getMessage());
                err.printStackTrace();

                System.out.println("Ignoring error and sleeping 10s");
                Thread.sleep(10000);
            }
        }
    }

    private void writeData(final Study study) throws IOException, NotAuthorizedException {
        final String dataPath = getDataPath(study);

        final ResultStream spssStream = study.download(FileFormat.CSV, null);
        Files.copy(spssStream, new File(dataPath).toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private void writeMetadata(final Study study)
        throws IOException, JsonIOException, NotAuthorizedException {
        final String metaPath = getMetaPath(study);

        final FileWriter fileWriter = new FileWriter(metaPath);

        final Map<String, Object> meta = new HashMap<>();
        meta.put("id", study.getId());
        meta.put("label", study.getLabel());
        meta.put("timestamp", dateFormat.format(study.getTimeStamp()));
        meta.put("variables", getVariables(study));

        gson.toJson(meta, fileWriter);
        fileWriter.close();
    }

    private Map<String, Map<String, Object>> getVariables(final Study study)
        throws NotAuthorizedException, IOException {

        final DdiList<Variable> variables = study.getVariables();
        final Map<String, Map<String, Object>> jsonVars = new HashMap<>();

        for (final Variable var : variables) {
            final List<Map<String, String>> categories = new ArrayList<>();

            for (final Category cat : var.getCategories()) {
                final Map<String, String> catMap = new HashMap<>();

                catMap.put("id", cat.getId());
                catMap.put("label", cat.getLabel());
                catMap.put("value", cat.getValue());

                categories.add(catMap);
            }

            final Map<String, Object> jsonVar = new HashMap<>();
            jsonVar.put("id", var.getId());
            jsonVar.put("label", var.getLabel());
            jsonVar.put("name", var.getName());
            jsonVar.put("categories", categories);

            jsonVars.put(var.getId(), jsonVar);
        }
        return jsonVars;
    }

    public static void main(final String[] args)
        throws IOException, URISyntaxException, NotAuthorizedException, InterruptedException {

        final String server = System.getProperty("nesstar.server");
        final String studyKey = System.getProperty("nesstar.study");
        final String username = System.getProperty("nesstar.username");
        final String password = System.getProperty("nesstar.password");
        final String outputPath = System.getProperty("nesstar.output");

        if (server == null) {
            throw new IllegalArgumentException("must set -Dnesstar.server");
        }

        final NesstarStudyDownloader downloader = new NesstarStudyDownloader(
            new URI(server), username, password, outputPath == null ? "data/" : outputPath);

        if (studyKey == null) {
            downloader.downloadAllStudies();
        } else {
            downloader.downloadStudyFor(studyKey);
        }
    }
}
