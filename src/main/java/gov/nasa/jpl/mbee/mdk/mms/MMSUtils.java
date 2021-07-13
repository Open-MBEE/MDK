package gov.nasa.jpl.mbee.mdk.mms;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.nomagic.ci.persistence.IProject;
import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.core.ProjectUtilities;
import com.nomagic.task.ProgressStatus;
import com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import gov.nasa.jpl.mbee.mdk.MDKPlugin;
import gov.nasa.jpl.mbee.mdk.api.incubating.MDKConstants;
import gov.nasa.jpl.mbee.mdk.api.incubating.convert.Converters;
import gov.nasa.jpl.mbee.mdk.http.ServerException;
import gov.nasa.jpl.mbee.mdk.json.JacksonUtils;
import gov.nasa.jpl.mbee.mdk.mms.actions.MMSLogoutAction;
import gov.nasa.jpl.mbee.mdk.mms.endpoints.*;
import gov.nasa.jpl.mbee.mdk.options.MDKOptionsGroup;
import gov.nasa.jpl.mbee.mdk.util.*;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.*;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import javax.swing.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

public class MMSUtils {

    private static final int CHECK_CANCEL_DELAY = 100;
    private static final AtomicReference<Exception> LAST_EXCEPTION = new AtomicReference<>();
    private static final Cache<Project, String> PROFILE_SERVER_CACHE = CacheBuilder.newBuilder().weakKeys().maximumSize(100).expireAfterAccess(10, TimeUnit.MINUTES).build();

    public enum HttpRequestType {
        GET, POST, PUT, DELETE
    }

    public enum JsonBlobType {
        ELEMENT_JSON, ELEMENT_ID, ARTIFACT_JSON, ARTIFACT_ID, PROJECT, REF, ORG, SEARCH
    }

    public static AtomicReference<Exception> getLastException() {
        return LAST_EXCEPTION;
    }

    public static ObjectNode getElement(Project project, String elementId, ProgressStatus progressStatus)
            throws IOException, ServerException, URISyntaxException, GeneralSecurityException {
        Collection<String> elementIds = new ArrayList<>(1);
        elementIds.add(elementId);
        File responseFile = getElementsRecursively(project, elementIds, progressStatus);
        try (JsonParser responseParser = JacksonUtils.getJsonFactory().createParser(responseFile)) {
            ObjectNode response = JacksonUtils.parseJsonObject(responseParser);
            JsonNode value;
            if (((value = response.get("elements")) != null) && value.isArray()
                    && (value = ((ArrayNode) value).remove(1)) != null && (value instanceof ObjectNode)) {
                return (ObjectNode) value;
            }
        }
        return null;
    }

    /**
     * @param project        project to check
     * @param elementIds     collection of elements to get mms data for
     * @param progressStatus progress status object, can be null
     * @return object node response
     * @throws ServerException
     * @throws IOException
     * @throws URISyntaxException
     */
    public static File getElementsRecursively(Project project, Collection<String> elementIds, ProgressStatus progressStatus)
            throws ServerException, IOException, URISyntaxException, GeneralSecurityException {
        // verify elements
        if (elementIds == null || elementIds.isEmpty()) {
            return null;
        }

        // create request file
        File sendData = createEntityFile(MMSUtils.class, ContentType.APPLICATION_JSON, elementIds, JsonBlobType.ELEMENT_ID);

        HttpRequestBase elementPutRequest = MMSUtils.prepareEndpointBuilderBasicJsonPutRequest(MMSElementsEndpoint.builder(), project, sendData)
                .addParam(MMSEndpointBuilderConstants.URI_PROJECT_SUFFIX, Converters.getIProjectToIdConverter().apply(project.getPrimaryProject()))
                .addParam(MMSEndpointBuilderConstants.URI_REF_SUFFIX, MDUtils.getBranchId(project)).build();

        //do cancellable request if progressStatus exists
        return sendMMSRequest(project, elementPutRequest, progressStatus);
    }

    public static File getArtifacts(Project project, Collection<String> artifactIds, ProgressStatus progressStatus) throws ServerException, IOException, URISyntaxException, GeneralSecurityException {
        if (artifactIds == null || artifactIds.isEmpty()) {
            return null;
        }
        File sendData = createEntityFile(MMSUtils.class, ContentType.APPLICATION_JSON, artifactIds, JsonBlobType.ARTIFACT_ID);
        HttpRequestBase artifactGetRequest = prepareEndpointBuilderBasicJsonPutRequest(MMSElementsEndpoint.builder(), project, sendData)
                .addParam(MMSEndpointBuilderConstants.URI_PROJECT_SUFFIX, Converters.getIProjectToIdConverter().apply(project.getPrimaryProject()))
                .addParam(MMSEndpointBuilderConstants.URI_REF_SUFFIX, MDUtils.getBranchId(project)).build();
        return sendMMSRequest(project, artifactGetRequest, progressStatus);
    }

    public static boolean validateJwtToken(Project project, ProgressStatus progressStatus) throws ServerException,
            IOException, URISyntaxException, GeneralSecurityException {
        // build request
        HttpRequestBase request = prepareEndpointBuilderBasicGet(MMSValidateJwtToken.builder(), project).build();
        
        // do request
        ObjectNode responseJson = JacksonUtils.getObjectMapper().createObjectNode();
        sendMMSRequest(project, request, progressStatus, responseJson);

        // parse response
        JsonNode value;
        return responseJson != null && (value = responseJson.get("username")) != null && value.isTextual() && !value.asText().isEmpty();
    }

    public static String validateCredentialsTicket(Project project, String ticket, ProgressStatus progressStatus)
            throws ServerException, IOException, URISyntaxException, GeneralSecurityException {
        //build request
        HttpRequestBase request = prepareEndpointBuilderBasicGet(MMSLoginEndpoint.builder(), project).build();

        // do request
        ObjectNode responseJson = JacksonUtils.getObjectMapper().createObjectNode();
        sendMMSRequest(project, request, progressStatus, responseJson);

        // parse response
        JsonNode value;
        if (responseJson != null && (value = responseJson.get("username")) != null && value.isTextual() && !value.asText().isEmpty()) {
            return value.asText();
        }
        return "";
    }

    public static File createEntityFile(Class<?> clazz, ContentType contentType, Collection<?> nodes, JsonBlobType jsonBlobType) throws IOException {
        File requestFile = File.createTempFile(clazz.getSimpleName() + "-" + contentType.getMimeType().replace('/', '-') + "-", null);
        if (MDKOptionsGroup.getMDKOptions().isLogJson()) {
            System.out.println("[INFO] Request Body: " + requestFile.getPath());
            Application.getInstance().getGUILog().log("[INFO] Request Body: " + requestFile.getPath());
        }
        else {
            if(!requestFile.delete()) { // if we cannot immediately delete we'll get it later
                requestFile.deleteOnExit();
            }
        }

        String arrayName = null;
        switch (jsonBlobType) {
            case ELEMENT_ID:
            case ELEMENT_JSON:
                arrayName = "elements";
                break;
            case ARTIFACT_ID:
            case ARTIFACT_JSON:
                arrayName = "artifacts";
                break;
            case ORG:
                arrayName = "orgs";
                break;
            case PROJECT:
                arrayName = "projects";
                break;
            case REF:
                arrayName = "refs";
                break;
        }

        try (FileOutputStream outputStream = new FileOutputStream(requestFile);
             JsonGenerator jsonGenerator = JacksonUtils.getJsonFactory().createGenerator(outputStream)) {

            jsonGenerator.writeStartObject();
            if(jsonBlobType != JsonBlobType.SEARCH) {
                jsonGenerator.writeArrayFieldStart(arrayName);
            }

            for (Object node : nodes) {
                if (node instanceof ObjectNode && jsonBlobType == JsonBlobType.ELEMENT_JSON || jsonBlobType == JsonBlobType.ORG || jsonBlobType == JsonBlobType.PROJECT || jsonBlobType == JsonBlobType.REF) {
                    jsonGenerator.writeObject(node);
                } else if (node instanceof String && jsonBlobType == JsonBlobType.ELEMENT_ID || jsonBlobType == JsonBlobType.ARTIFACT_ID) {
                    jsonGenerator.writeStartObject();
                    jsonGenerator.writeStringField(MDKConstants.ID_KEY, (String) node);
                    jsonGenerator.writeEndObject();
                } else if (node instanceof String && jsonBlobType == JsonBlobType.SEARCH) {
                    jsonGenerator.writeObjectFieldStart(MDKConstants.PARAMS_FIELD);
                    Project project = Application.getInstance().getProject();
                    if(project != null && node.equals(Converters.getIProjectToIdConverter().apply(project.getPrimaryProject()))) {
                        jsonGenerator.writeStringField(MDKConstants.OWNER_ID_KEY, (String) node);
                    } else {
                        jsonGenerator.writeStringField(MDKConstants.ID_KEY, (String) node);
                    }
                    jsonGenerator.writeEndObject();
                    jsonGenerator.writeObjectFieldStart(MDKConstants.RECURSE_FIELD);
                    jsonGenerator.writeStringField(MDKConstants.ID_KEY, MDKConstants.OWNER_ID_KEY);
                    jsonGenerator.writeEndObject();
                }
                else {
                    throw new IOException("Unsupported collection type for entity file.");
                }
            }

            if(jsonBlobType != JsonBlobType.SEARCH) {
                jsonGenerator.writeEndArray();
            }

            jsonGenerator.writeStringField("source", "magicdraw");
            jsonGenerator.writeStringField("mdkVersion", MDKPlugin.getInstance().getDescriptor().getVersion());
            jsonGenerator.writeEndObject();
        }

        return requestFile;
    }

    /**
     * General purpose method for sending a constructed http request via http client. For streaming reasons, defaults to writing to a file.
     * When the file is written, it is temp unless the logJSON environment variable is enabled (or DEVELOPER mode is on). This file IS NOT
     * written to when the responseJson object is non-null. In that instance, the response is parsed into this object instead, keeping
     * the results entirely in memory. For large results this could be extremely memory intensive, so it is not advised for general use.
     *
     * @param request
     * @return
     * @throws IOException
     * @throws ServerException
     */
    public static File sendMMSRequest(Project project, HttpRequestBase request, ProgressStatus progressStatus, final ObjectNode responseJson) throws IOException, ServerException, GeneralSecurityException {
        final File responseFile = (responseJson == null ? File.createTempFile("Response-", null) : null);
        final AtomicReference<String> responseBody = new AtomicReference<>();
        final AtomicReference<Integer> responseCode = new AtomicReference<>();

        String requestSummary = "[INFO] MMS Request [" + request.getMethod() + "] " + request.getURI().toString();
        System.out.println(requestSummary);
        if (MDUtils.isDeveloperMode()) {
            Application.getInstance().getGUILog().log(requestSummary);
        }

        // create client, execute request, parse response, store in thread safe buffer to return as string later
        // client, response, and reader are all auto closed after block
        if (progressStatus == null) {
            try (CloseableHttpClient httpclient = HttpClients.createDefault();
                 CloseableHttpResponse response = httpclient.execute(request);
                 InputStream inputStream = response.getEntity().getContent()) {
                responseCode.set(response.getStatusLine().getStatusCode());
                String responseSummary = "[INFO] MMS Response [" + request.getMethod() + "]: " + responseCode.get() + " " + request.getURI().toString();
                System.out.println(responseSummary);
                if (MDUtils.isDeveloperMode()) {
                    Application.getInstance().getGUILog().log(responseSummary);
                }
                if (inputStream != null) {
                    responseBody.set(generateMmsOutput(inputStream, responseFile));
                }
            }
        }
        else {
            LAST_EXCEPTION.set(null);
            progressStatus.setIndeterminate(true);
            Future<?> future = TaskRunner.runWithProgressStatus(() -> {
                try (CloseableHttpClient httpclient = HttpClients.createDefault();
                     CloseableHttpResponse response = httpclient.execute(request);
                     InputStream inputStream = response.getEntity().getContent()) {
                    responseCode.set(response.getStatusLine().getStatusCode());
                    if (MDKOptionsGroup.getMDKOptions().isLogJson()) {
                        System.out.println("[INFO] MMS Response [" + request.getMethod() + "]: " + responseCode.get() + " " + request.getURI().toString());
                    }
                    if (inputStream != null) {
                        responseBody.set(generateMmsOutput(inputStream, responseFile));
                    }
                } catch (Exception e) {
                    LAST_EXCEPTION.set(e);
                    e.printStackTrace();
                }
            }, null, TaskRunner.ThreadExecutionStrategy.NONE, true);
            try {
                while (!future.isDone() && !future.isCancelled()) {
                    try {
                        future.get(CHECK_CANCEL_DELAY, TimeUnit.MILLISECONDS);
                    } catch (ExecutionException | TimeoutException ignored) {

                    } catch (InterruptedException e2) {
                        Thread.currentThread().interrupt();
                    }
                    if (progressStatus.isCancel() && future.cancel(true)) {
                        Application.getInstance().getGUILog().log("[INFO] MMS request was manually cancelled.");
                        return null;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (LAST_EXCEPTION.get() instanceof IOException) {
                throw (IOException) LAST_EXCEPTION.get();
            }
        }
        if (responseFile == null) {
            try (InputStream inputStream = new ByteArrayInputStream(responseBody.get().getBytes())) {
                if (!processResponse(responseCode.get(), inputStream, project)) {
                    throw new ServerException(responseBody.get(), responseCode.get());
                }
                ObjectNode json = JacksonUtils.getObjectMapper().readValue(responseBody.get(), ObjectNode.class);
                Iterator<Map.Entry<String, JsonNode>> jsonFields = json.fields();
                while (jsonFields.hasNext()) {
                    Map.Entry<String, JsonNode> currentField = jsonFields.next();
                    responseJson.put(currentField.getKey(), currentField.getValue());
                }
            }
        }
        else {
            try (InputStream inputStream = new FileInputStream(responseFile)) {
                if (!processResponse(responseCode.get(), inputStream, project)) {
                    throw new ServerException(responseFile.getAbsolutePath(), responseCode.get());
                }
            }
        }

        return responseFile;
    }

    public static File sendMMSRequest(Project project, HttpRequestBase request) throws IOException, ServerException, URISyntaxException, GeneralSecurityException {
        return sendMMSRequest(project, request, null, null);
    }

    public static File sendMMSRequest(Project project, HttpRequestBase request, ProgressStatus progressStatus) throws IOException, ServerException, URISyntaxException, GeneralSecurityException {
        return sendMMSRequest(project, request, progressStatus, null);
    }

    private static String generateMmsOutput(InputStream inputStream, final File responseFile) throws IOException {
        if (responseFile != null) {
            try (OutputStream outputStream = new FileOutputStream(responseFile)) {
                byte[] buffer = new byte[8 * 1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                if (MDKOptionsGroup.getMDKOptions().isLogJson()) {
                    System.out.println("[INFO] Response Body: " + responseFile.getPath());
                    Application.getInstance().getGUILog().log("[INFO] Response Body: " + responseFile.getPath());
                }
                else {
                    if(!responseFile.delete()) { // if we cannot immediately delete we'll get it later
                        responseFile.deleteOnExit();
                    }
                }
            }
            return "";
        }
        else {
            return IOUtils.toString(inputStream);
        }
    }

    private static boolean processResponse(int responseCode, InputStream responseStream, Project project) {
        boolean throwServerException = false;
        //TODO print out server messages from responseStream

        if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
            Application.getInstance().getGUILog().log("[ERROR] MMS authentication is missing or invalid. Closing connections. Please log in again and your request will be retried.");
            if (project != null) {
                MMSLogoutAction.logoutAction(project);
            }
            throwServerException = true;
        }
        // if we got messages out, we hit a valid endpoint and got a valid response and either a 200 or a 404 is an acceptable response code. If not, throw is already true.
        else if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_NOT_FOUND) {
            throwServerException = true;
        }

        /*
        if (throwServerException) {
            // big flashing red letters that the action failed, or as close as we're going to get
            Application.getInstance().getGUILog().log("<span style=\"color:#FF0000; font-weight:bold\">[ERROR] Operation failed due to server error. Server code: " + responseCode + "</span>" +
                    "<span style=\"color:#FFFFFF; font-weight:bold\"> !!!!!</span>"); // hidden characters for easy search
        }
        */
        return !throwServerException;
    }

    /**
     * @param project
     * @return
     * @throws IllegalStateException
     */
    public static String getServerUrl(Project project) throws IllegalStateException {
        String urlString;
        if (project == null) {
            throw new IllegalStateException("Project is null.");
        }
        Element primaryModel = project.getPrimaryModel();
        if (primaryModel == null) {
            throw new IllegalStateException("Model is null.");
        }

        if (StereotypesHelper.hasStereotype(primaryModel, "ModelManagementSystem")) {
            urlString = (String) StereotypesHelper.getStereotypePropertyFirst(primaryModel, "ModelManagementSystem", "MMS URL");
        }
        else if (ProjectUtilities.isStandardSystemProfile(project.getPrimaryProject())) {
            urlString = PROFILE_SERVER_CACHE.getIfPresent(project);
            if (urlString == null) {
                urlString = JOptionPane.showInputDialog("Specify server URL for standard profile.", null);
            }
            if (urlString == null || urlString.trim().isEmpty()) {
                return null;
            }
            PROFILE_SERVER_CACHE.put(project, urlString);
        }
        else {
            Utils.showPopupMessage("The root element does not have the ModelManagementSystem stereotype.\nPlease apply it and specify the server information.");
            return null;
        }
        if (urlString == null || urlString.isEmpty()) {
            return null;
        }
        return urlString.trim();
    }

    public static String getMmsOrg(Project project)
            throws IOException, URISyntaxException, ServerException, GeneralSecurityException {
        HttpRequestBase request = prepareEndpointBuilderBasicGet(MMSProjectsEndpoint.builder(), project).build();
        File responseFile = sendMMSRequest(project, request);
        try (JsonParser responseParser = JacksonUtils.getJsonFactory().createParser(responseFile)) {
            ObjectNode response = JacksonUtils.parseJsonObject(responseParser);
            JsonNode arrayNode;
            if (((arrayNode = response.get("projects")) != null) && arrayNode.isArray()) {
                JsonNode projectId, orgId;
                for (JsonNode projectNode : arrayNode) {
                    if (((projectId = projectNode.get(MDKConstants.ID_KEY)) != null) && projectId.isTextual() && projectId.asText().equals(Converters.getIProjectToIdConverter().apply(project.getPrimaryProject()))
                            && ((orgId = projectNode.get(MDKConstants.ORG_ID_KEY)) != null) && orgId.isTextual() && !orgId.asText().isEmpty()) {
                        return orgId.asText();
                    }
                }
            }
        }
        return null;
    }

    /**
     * Returns a URIBuilder object with a path = "/alfresco/service". Used as the base for all of the rest of the
     * URIBuilder generating convenience classes.
     *
     * @param project The project to gather the mms url and site name information from
     * @return URIBuilder
     * @throws URISyntaxException
     */
    public static URIBuilder getServiceUri(Project project) {
        return getServiceUri(project, null);
    }

    public static URIBuilder getServiceUri(String baseUrl) {
        return getServiceUri(null, baseUrl);
    }

    private static URIBuilder getServiceUri(Project project, String baseUrl) {
        String urlString = project == null ? baseUrl : getServerUrl(project);
        if (urlString == null) {
            return null;
        }

        // [scheme:][//host][path][?query][#fragment]

        URIBuilder uri;
        try {
            uri = new URIBuilder(urlString);
        } catch (URISyntaxException e) {
            Application.getInstance().getGUILog().log("[ERROR] Unexpected error in generation of MMS URL for project. Reason: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
        String path = Optional.ofNullable(uri.getPath()).orElse("");
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        uri.setPath(path);
        return uri;

    }

    public static MMSEndpoint.Builder prepareEndpointBuilderBasicGet(MMSEndpoint.Builder builder, Project project) {
        return prepareEndpointBuilderBasicRequest(builder, project, HttpRequestType.GET, null, null);
    }

    public static MMSEndpoint.Builder prepareEndpointBuilderBasicJsonPostRequest(MMSEndpoint.Builder builder, Project project, File file) {
        return prepareEndpointBuilderBasicRequest(builder, project, HttpRequestType.POST, ContentType.APPLICATION_JSON, file);
    }

    public static MMSEndpoint.Builder prepareEndpointBuilderBasicJsonPutRequest(MMSEndpoint.Builder builder, Project project, File file) {
        return prepareEndpointBuilderBasicRequest(builder, project, HttpRequestType.PUT, ContentType.APPLICATION_JSON, file);
    }

    public static MMSEndpoint.Builder prepareEndpointBuilderBasicJsonDeleteRequest(MMSEndpoint.Builder builder, Project project, File file) {
        return prepareEndpointBuilderBasicRequest(builder, project, HttpRequestType.DELETE, ContentType.APPLICATION_JSON, file);
    }

    public static MMSEndpoint.Builder prepareEndpointBuilderBasicRequest(MMSEndpoint.Builder builder, Project project, HttpRequestType requestType, ContentType contentType, File file) {
        return prepareEndpointBuilderGenericRequest(builder, MMSUtils.getServerUrl(project), project, requestType, contentType, file);
    }

    public static MMSEndpoint.Builder prepareEndpointBuilderGenericRequest(MMSEndpoint.Builder builder, String uri, Project project, HttpRequestType requestType, ContentType contentType, File file) {
        return builder.addParam(MMSEndpointBuilderConstants.URI_BASE_PATH, uri)
                .addParam(MMSEndpointBuilderConstants.HTTP_REQUEST_TYPE, requestType)
                .addParam(MMSEndpointBuilderConstants.MAGICDRAW_PROJECT, project)
                .addParam(MMSEndpointBuilderConstants.REST_CONTENT_TYPE, contentType)
                .addParam(MMSEndpointBuilderConstants.REST_DATA_FILE, file);
    }
}
