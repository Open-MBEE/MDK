package gov.nasa.jpl.mbee.mdk.mms;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.nomagic.ci.persistence.IProject;
import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.core.ProjectUtilities;
import com.nomagic.magicdraw.esi.EsiUtils;
import com.nomagic.task.ProgressStatus;
import com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;

import gov.nasa.jpl.mbee.mdk.MDKPlugin;
import gov.nasa.jpl.mbee.mdk.api.incubating.MDKConstants;
import gov.nasa.jpl.mbee.mdk.api.incubating.convert.Converters;
import gov.nasa.jpl.mbee.mdk.http.HttpDeleteWithBody;
import gov.nasa.jpl.mbee.mdk.http.ServerException;
import gov.nasa.jpl.mbee.mdk.json.JacksonUtils;
import gov.nasa.jpl.mbee.mdk.util.MDUtils;
import gov.nasa.jpl.mbee.mdk.util.TicketUtils;
import gov.nasa.jpl.mbee.mdk.util.Utils;
import gov.nasa.jpl.mbee.mdk.mms.actions.MMSLogoutAction;
import gov.nasa.jpl.mbee.mdk.options.MDKOptionsGroup;

import org.apache.commons.io.IOUtils;
import org.apache.http.Consts;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.client.entity.EntityBuilder;
import org.apache.http.client.methods.*;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.FileEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.*;

public class MMSUtils {

    private static final int CHECK_CANCEL_DELAY = 100;

    private static String developerUrl = "";

    public enum HttpRequestType {
        GET, POST, PUT, DELETE
    }

    private enum ThreadRequestExceptionType {
        IO_EXCEPTION, SERVER_EXCEPTION, URI_SYNTAX_EXCEPTION
    }

    public enum JsonBlobType {
        ELEMENT_JSON, ELEMENT_ID, PROJECT, REF, ORG
    }

    public static ObjectNode getElement(Project project, String elementId, ProgressStatus progressStatus)
            throws IOException, ServerException, URISyntaxException {
        Collection<String> elementIds = new ArrayList<>(1);
        elementIds.add(elementId);
        File responseFile = getElementsRecursively(project, elementIds, 0, progressStatus);
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

    public static File getElementRecursively(Project project, String elementId, int depth, ProgressStatus progressStatus)
            throws IOException, ServerException, URISyntaxException {
        Collection<String> elementIds = new ArrayList<>(1);
        elementIds.add(elementId);
        return getElementsRecursively(project, elementIds, depth, progressStatus);
    }

    /**
     * @param elementIds     collection of elements to get mms data for
     * @param project        project to check
     * @param progressStatus progress status object, can be null
     * @return object node response
     * @throws ServerException
     * @throws IOException
     * @throws URISyntaxException
     */
    public static File getElements(Project project, Collection<String> elementIds, ProgressStatus progressStatus)
            throws IOException, ServerException, URISyntaxException {
        return getElementsRecursively(project, elementIds, 0, progressStatus);
    }

    /**
     * @param elementIds     collection of elements to get mms data for
     * @param depth          depth to recurse through child elements. takes priority over recurse field
     * @param project        project to check
     * @param progressStatus progress status object, can be null
     * @return object node response
     * @throws ServerException
     * @throws IOException
     * @throws URISyntaxException
     */
    public static File getElementsRecursively(Project project, Collection<String> elementIds, int depth, ProgressStatus progressStatus)
            throws ServerException, IOException, URISyntaxException {
/* ORIGINAL VERSION - uses MMS recursion instead of recursive depth 1 calls from MDK
        // verify elements
        if (elementIds == null || elementIds.isEmpty()) {
            return null;
        }

        // build uri
        URIBuilder requestUri = getServiceProjectsRefsElementsUri(project);
        if (requestUri == null) {
            return null;
        }
        if (depth == -1 || depth > 0) {
            requestUri.setParameter("depth", java.lang.Integer.toString(depth));
        }

        // create request file
        File sendData = createEntityFile(MMSUtils.class, ContentType.APPLICATION_JSON, elementIds, JsonBlobType.ELEMENT_ID);

        //do cancellable request if progressStatus exists
        Utils.guilog("[INFO] Searching for " + elementIds.size() + " elements from server...");
        if (progressStatus != null) {
            return sendCancellableMMSRequest(project, MMSUtils.buildRequest(MMSUtils.HttpRequestType.PUT, requestUri, sendData, ContentType.APPLICATION_JSON), progressStatus);
        }
        return sendMMSRequest(project, MMSUtils.buildRequest(MMSUtils.HttpRequestType.PUT, requestUri, sendData, ContentType.APPLICATION_JSON));
END ORIGINAL VERSION */

        // verify elements
        if (elementIds == null || elementIds.isEmpty()) {
            return null;
        }

        // build uri
        URIBuilder requestUri = getServiceProjectsRefsElementsUri(project);
        if (requestUri == null) {
            return null;
        }

        HashSet<String> processedIds = new HashSet<>();
        File targetFile = File.createTempFile("RecursiveResponse-", null);
        targetFile.deleteOnExit();
        try (FileOutputStream outputStream = new FileOutputStream(targetFile);
             JsonGenerator jsonGenerator = JacksonUtils.getJsonFactory().createGenerator(outputStream)) {
            jsonGenerator.writeStartObject();
            jsonGenerator.writeArrayFieldStart("elements");
            for (String elementId : elementIds) {
                getElementsRecursivelyHelper(project, requestUri, elementId, depth, processedIds, jsonGenerator, progressStatus);
            }
            jsonGenerator.writeEndArray();
            jsonGenerator.writeEndObject();
        }
        System.out.println("Compiled Response Body: " + targetFile.getPath());
        return targetFile;
    }

    public static void getElementsRecursivelyHelper(Project project, URIBuilder requestUri, String targetId, int depth, Set<String> processedIds, JsonGenerator jsonGenerator, ProgressStatus progressStatus)
            throws ServerException, IOException, URISyntaxException {
        if (depth == 0) {
            requestUri.setParameter("depth", "0");
        } else {
            requestUri.setParameter("depth", "1");
        }

        // create request file
        File sendData = createEntityFile(MMSUtils.class, ContentType.APPLICATION_JSON, Collections.singletonList(targetId), JsonBlobType.ELEMENT_ID);

        //do cancellable request if progressStatus exists
        processedIds.add(targetId);
        File currentResponse;
        if (progressStatus != null) {
            currentResponse = sendCancellableMMSRequest(project, MMSUtils.buildRequest(MMSUtils.HttpRequestType.PUT, requestUri, sendData, ContentType.APPLICATION_JSON), progressStatus);
        } else {
            currentResponse = sendMMSRequest(project, MMSUtils.buildRequest(MMSUtils.HttpRequestType.PUT, requestUri, sendData, ContentType.APPLICATION_JSON));
        }
        if (currentResponse == null) {
            return;
        }
        try (JsonParser jsonParser = JacksonUtils.getJsonFactory().createParser(currentResponse)) {
            // find elements array
            JsonToken current;
            while ((current = jsonParser.nextToken()) != null) {
                if (current == JsonToken.FIELD_NAME && jsonParser.getCurrentName().equals("elements")) {
                    while ((current = jsonParser.nextToken()) != JsonToken.END_ARRAY && current != null) {
                        if (current == JsonToken.START_OBJECT) {
                            ObjectNode node = JacksonUtils.parseJsonObject(jsonParser);
                            JsonNode idValue;
                            if ((idValue = node.get(MDKConstants.ID_KEY)) != null && idValue.isTextual()) {
                                if (idValue.asText().equals(targetId)) {
                                    // if id == targetId, add objectNode to jsonGenerator output
                                    jsonGenerator.writeObject(node);
                                } else if (!processedIds.contains(idValue.asText()) && depth != 0) {
                                    // else if id not in processedIds, run this function on that id
                                    getElementsRecursivelyHelper(project, requestUri, idValue.asText(), depth - 1, processedIds, jsonGenerator, progressStatus);
                                }
                            }
                        }
                    }
                }
            }
        }
        currentResponse.delete();
        sendData.delete();
    }

    /**
     * General purpose method for making http requests for file upload.
     *
     * @param requestUri URI to send the request to. Methods to generate this URI are available in the class.
     * @param sendFile   File to send as an entity/body along with the request
     * @return
     * @throws IOException
     * @throws URISyntaxException
     */
    public static HttpRequestBase buildImageRequest(URIBuilder requestUri, File sendFile)
            throws IOException, URISyntaxException {
        URI requestDest = requestUri.build();
        HttpPost requestUpload = new HttpPost(requestDest);
        EntityBuilder uploadBuilder = EntityBuilder.create();
        uploadBuilder.setFile(sendFile);
        requestUpload.setEntity(uploadBuilder.build());
        requestUpload.addHeader("Content-Type", "image/svg");
        return requestUpload;
    }

    /**
     * General purpose method for making http requests for JSON objects. Type of request is specified in method call.
     *
     * @param type       Type of request, as selected from one of the options in the inner enum.
     * @param requestUri URI to send the request to. Methods to generate this URI are available in the class.
     * @param sendData   Data to send as an entity/body along with the request, if desired. Support for GET and DELETE
     *                   with body is included.
     * @return
     * @throws IOException
     * @throws URISyntaxException
     */
    public static HttpRequestBase buildRequest(HttpRequestType type, URIBuilder requestUri, File sendData, ContentType contentType)
            throws IOException, URISyntaxException {
        // build specified request type
        // assume that any request can have a body, and just build the appropriate one
        URI requestDest = requestUri.build();
        HttpRequestBase request = null;
        // bulk GETs are not supported in MMS, but bulk PUTs are. checking and and throwing error here in case
        if (type == HttpRequestType.GET && sendData != null) {
            throw new IOException("GETs with body are not supported");
        }
        switch (type) {
            case DELETE:
                request = new HttpDeleteWithBody(requestDest);
                break;
            case GET:
//                request = new HttpGetWithBody(requestDest);
                request = new HttpGet(requestDest);
                break;
            case POST:
                request = new HttpPost(requestDest);
                break;
            case PUT:
                request = new HttpPut(requestDest);
                break;
        }
        request.addHeader("charset", (contentType != null ? contentType.getCharset() : Consts.UTF_8).displayName());
        if (sendData != null) {
            if (contentType != null) {
                request.addHeader("Content-Type", contentType.getMimeType());
            }
            HttpEntity reqEntity = new FileEntity(sendData, contentType);
            //reqEntity.setChunked(true);
            ((HttpEntityEnclosingRequest) request).setEntity(reqEntity);
        }
        return request;
    }

    /**
     * Convenience / clarity method for making http requests for JSON objects withoout body. Type of request is
     * specified in method call.
     *
     * @param type       Type of request, as selected from one of the options in the inner enum.
     * @param requestUri URI to send the request to. Methods to generate this URI are available in the class.
     * @return
     * @throws IOException
     * @throws URISyntaxException
     */
    public static HttpRequestBase buildRequest(HttpRequestType type, URIBuilder requestUri)
            throws IOException, URISyntaxException {
        return buildRequest(type, requestUri, null, null);
    }

    public static File createEntityFile(Class<?> clazz, ContentType contentType, Collection nodes, JsonBlobType jsonBlobType)
            throws IOException {
        File file = File.createTempFile(clazz.getSimpleName() + "-" + contentType.getMimeType().replace('/', '-') + "-", null);
        file.deleteOnExit();

        String arrayName = "elements";
        if (jsonBlobType == JsonBlobType.ORG) {
            arrayName = "orgs";
        }
        else if (jsonBlobType == JsonBlobType.PROJECT) {
            arrayName = "projects";
        }
        else if (jsonBlobType == JsonBlobType.REF) {
            arrayName = "refs";
        }
        try (FileOutputStream outputStream = new FileOutputStream(file);
                JsonGenerator jsonGenerator = JacksonUtils.getJsonFactory().createGenerator(outputStream)) {
            jsonGenerator.writeStartObject();
            jsonGenerator.writeArrayFieldStart(arrayName);
            for (Object node : nodes) {
                if (node instanceof ObjectNode && jsonBlobType == JsonBlobType.ELEMENT_JSON || jsonBlobType == JsonBlobType.ORG || jsonBlobType == JsonBlobType.PROJECT || jsonBlobType == JsonBlobType.REF) {
                    jsonGenerator.writeObject(node);
                }
                else if (node instanceof String && jsonBlobType == JsonBlobType.ELEMENT_ID) {
                    jsonGenerator.writeStartObject();
                    jsonGenerator.writeStringField(MDKConstants.ID_KEY, (String) node);
                    jsonGenerator.writeEndObject();
                }
                else {
                    throw new IOException("Unsupported collection type for entity file.");
                }
            }
            jsonGenerator.writeEndArray();
            jsonGenerator.writeStringField("source", "magicdraw");
            jsonGenerator.writeStringField("mdkVersion", MDKPlugin.VERSION);
            jsonGenerator.writeEndObject();
        }
        System.out.println("Request Body: " + file.getPath());
        return file;
    }

    /**
     * General purpose method for sending a constructed http request via http client.
     *
     * @param request
     * @return
     * @throws IOException
     * @throws ServerException
     */
    public static File sendMMSRequest(Project project, HttpRequestBase request)
            throws IOException, ServerException, URISyntaxException {
        File targetFile = File.createTempFile("Response-", null);
        targetFile.deleteOnExit();
        HttpEntityEnclosingRequest httpEntityEnclosingRequest = null;
        boolean logBody = MDKOptionsGroup.getMDKOptions().isLogJson();
        logBody = logBody && request instanceof HttpEntityEnclosingRequest;
        logBody = logBody && ((httpEntityEnclosingRequest = (HttpEntityEnclosingRequest) request).getEntity() != null);
        logBody = logBody && httpEntityEnclosingRequest.getEntity().isRepeatable();
        System.out.println("MMS Request [" + request.getMethod() + "] " + request.getURI().toString());

        // flag for later server exceptions; they will be thrown after printing any available server messages to the gui log
        boolean throwServerException = false;
        int responseCode;

        // create client, execute request, parse response, store in thread safe buffer to return as string later
        // client, response, and reader are all auto closed after block
        try (CloseableHttpClient httpclient = HttpClients.createDefault();
             CloseableHttpResponse response = httpclient.execute(request);
             InputStream inputStream = response.getEntity().getContent();
             OutputStream outputStream = new FileOutputStream(targetFile)) {
            // get data out of the response
            responseCode = response.getStatusLine().getStatusCode();
            String responseType = ((response.getEntity().getContentType() != null) ? response.getEntity().getContentType().getValue() : "");

            // debug / logging output from response
            System.out.println("MMS Response [" + request.getMethod() + "] " + request.getURI().toString() + " - Code: " + responseCode);
            System.out.println("Response Body: " + targetFile.getPath());

            // assume that a GET that returns 404 with json response bodies is a "missing resource" 404, which are expected for some cases and should not break normal execution flow
            if (responseCode == HttpURLConnection.HTTP_OK || (request.getMethod().equals("GET") && responseCode == HttpURLConnection.HTTP_NOT_FOUND && responseType.equals("application/json;charset=UTF-8"))) {
                // continue
            }
            // allow re-attempt of request if credentials have expired or are invalid
            else if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                Utils.guilog("[ERROR] MMS authentication is missing or invalid. Closing connections. Please log in again and your request will be retried. Server code: " + responseCode);
                MMSLogoutAction.logoutAction(project);
                throwServerException = true;
            }
            // if it's anything else, assume failure and break normal flow
            else {
                Utils.guilog("[ERROR] Operation failed due to server error. Server code: " + responseCode);
                throwServerException = true;
            }

            // dump to temp response file, build jsonParser and print server message if possible
            if (inputStream != null && responseType.equals("application/json;charset=UTF-8")) {
                byte[] buffer = new byte[8 * 1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
        }

        JsonFactory jsonFactory = JacksonUtils.getJsonFactory();
        try (JsonParser jsonParser = jsonFactory.createParser(targetFile)) {
            while (jsonParser.nextFieldName() != null && !jsonParser.nextFieldName().equals("message")) {
                // spin until we find message
            }
            if (jsonParser.getCurrentToken() == JsonToken.FIELD_NAME) {
                jsonParser.nextToken();
                Application.getInstance().getGUILog().log("[SERVER MESSAGE] " + jsonParser.getText());
            }
        }

        if (throwServerException) {
            // big flashing red letters that the action failed, or as close as we're going to get
            Utils.showPopupMessage("Action failed. See notification window for details.");
            // throw is done last, after printing the error and any messages that might have been returned
            throw new ServerException(targetFile.getAbsolutePath(), responseCode);
        }
        return targetFile;
    }

    /**
     * General purpose method for running a cancellable request. Builds a new thread to run the request, and passes
     * any relevant exception information back out via atomic references and generates new exceptions in calling thread
     *
     * @param request
     * @param progressStatus
     * @return
     * @throws IOException
     * @throws URISyntaxException
     * @throws ServerException    contains both response code and response body
     */
    public static File sendCancellableMMSRequest(final Project project, HttpRequestBase request, ProgressStatus progressStatus)
            throws IOException, ServerException, URISyntaxException {
        final AtomicReference<File> responseFile = new AtomicReference<>();
        final AtomicReference<Integer> ecode = new AtomicReference<>();
        final AtomicReference<ThreadRequestExceptionType> etype = new AtomicReference<>();
        final AtomicReference<String> emsg = new AtomicReference<>();
        Thread t = new Thread(() -> {
            File response = null;
            try {
                response = sendMMSRequest(project, request);
                etype.set(null);
                ecode.set(200);
                emsg.set("");
            } catch (ServerException ex) {
                etype.set(ThreadRequestExceptionType.SERVER_EXCEPTION);
                ecode.set(ex.getCode());
                emsg.set(ex.getMessage());
                ex.printStackTrace();
            } catch (IOException e) {
                etype.set(ThreadRequestExceptionType.IO_EXCEPTION);
                emsg.set(e.getMessage());
                e.printStackTrace();
            } catch (URISyntaxException e) {
                etype.set(ThreadRequestExceptionType.URI_SYNTAX_EXCEPTION);
                emsg.set(e.getMessage());
                e.printStackTrace();
            }
            responseFile.set(response);
        });
        t.start();
        try {
            t.join(CHECK_CANCEL_DELAY);
            while (t.isAlive()) {
                if (progressStatus != null && progressStatus.isCancel()) {
                    Application.getInstance().getGUILog().log("[INFO] Request to server for elements cancelled.");
                    //clean up thread?
                    return null;
                }
                t.join(CHECK_CANCEL_DELAY);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (etype.get() == ThreadRequestExceptionType.SERVER_EXCEPTION) {
            throw new ServerException(emsg.get(), ecode.get());
        }
        else if (etype.get() == ThreadRequestExceptionType.IO_EXCEPTION) {
            throw new IOException(emsg.get());
        }
        else if (etype.get() == ThreadRequestExceptionType.URI_SYNTAX_EXCEPTION) {
            throw new URISyntaxException(request.getURI().toString(), emsg.get());
        }
        return responseFile.get();
    }

    public static String sendCredentials(Project project, String username, String password)
            throws ServerException, IOException, URISyntaxException {
        URIBuilder requestUri = MMSUtils.getServiceUri(project);
        if (requestUri == null) {
            return null;
        }
        requestUri.setPath(requestUri.getPath() + "/api/login");
        requestUri.clearParameters();
        ObjectNode credentials = JacksonUtils.getObjectMapper().createObjectNode();
        credentials.put("username", username);
        credentials.put("password", password);

        //build request
        ContentType contentType = ContentType.APPLICATION_JSON;
        URI requestDest = requestUri.build();
        HttpRequestBase request = new HttpPost(requestDest);

        request.addHeader("Content-Type", "application/json");
        request.addHeader("charset", (Consts.UTF_8).displayName());

        String data = JacksonUtils.getObjectMapper().writeValueAsString(credentials);
        ((HttpEntityEnclosingRequest) request).setEntity(new StringEntity(data, ContentType.APPLICATION_JSON));

        // do request
        System.out.println("MMS Request [POST] " + requestUri.toString());
        ObjectNode responseJson = null;
        try (CloseableHttpClient httpclient = HttpClients.createDefault();
             CloseableHttpResponse response = httpclient.execute(request);
             InputStream inputStream = response.getEntity().getContent()) {
            // get data out of the response
            int responseCode = response.getStatusLine().getStatusCode();
            String responseBody = ((inputStream != null) ? IOUtils.toString(inputStream) : "");
            String responseType = ((response.getEntity().getContentType() != null) ? response.getEntity().getContentType().getValue() : "");

            // debug / logging output from response
            System.out.println("MMS Response [POST] " + requestUri.toString() + " - Code: " + responseCode);

            // flag for later server exceptions; they will be thrown after printing any available server messages to the gui log
            boolean throwServerException = false;

            // if it's not 200, failure and break normal flow
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Application.getInstance().getGUILog().log("[ERROR] Operation failed due to server error. Server code: " + responseCode);
                throwServerException = true;
            }

            // print server message if possible
            if (!responseBody.isEmpty() && responseType.equals("application/json;charset=UTF-8")) {
                responseJson = JacksonUtils.getObjectMapper().readValue(responseBody, ObjectNode.class);
                JsonNode value;
                // display single response message
                if (responseJson != null && (value = responseJson.get("message")) != null && value.isTextual() && !value.asText().isEmpty()) {
                    Application.getInstance().getGUILog().log("[SERVER MESSAGE] " + value.asText());
                }
                // display multiple response messages
                if (responseJson != null && (value = responseJson.get("messages")) != null && value.isArray()) {
                    ArrayNode msgs = (ArrayNode) value;
                    for (JsonNode msg : msgs) {
                        if (msg != null && (value = msg.get("message")) != null && value.isTextual() && !value.asText().isEmpty()) {
                            Application.getInstance().getGUILog().log("[SERVER MESSAGE] " + value.asText());
                        }
                    }
                }
            }

            if (throwServerException) {
                // big flashing red letters that the action failed, or as close as we're going to get
                Utils.showPopupMessage("Action failed. See notification window for details.");
                // throw is done last, after printing the error and any messages that might have been returned
                throw new ServerException(responseBody, responseCode);
            }
        }
        // parse response
        JsonNode value;
        if (responseJson != null && (value = responseJson.get("data")) != null && (value = value.get("ticket")) != null && value.isTextual()) {
            return value.asText();
        }
        return null;
    }

    public static String validateCredentials(Project project, String ticket)
            throws ServerException, IOException, URISyntaxException {
        URIBuilder requestUri = MMSUtils.getServiceUri(project);
        if (requestUri == null) {
            return "";
        }
        requestUri.setPath(requestUri.getPath() + "/mms/login/ticket/" + ticket);
        requestUri.clearParameters();

        //build request
        URI requestDest = requestUri.build();
        HttpRequestBase request = new HttpGet(requestDest);

        // do request
        System.out.println("MMS Request [GET] " + requestUri.toString());
        ObjectNode responseJson;
        JsonNode value;
        try (CloseableHttpClient httpclient = HttpClients.createDefault();
             CloseableHttpResponse response = httpclient.execute(request);
             InputStream inputStream = response.getEntity().getContent()) {
            // get data out of the response
            int responseCode = response.getStatusLine().getStatusCode();
            String responseBody = ((inputStream != null) ? IOUtils.toString(inputStream) : "");
            String responseType = ((response.getEntity().getContentType() != null) ? response.getEntity().getContentType().getValue() : "");

            // debug / logging output from response
            System.out.println("MMS Response [GET] " + requestUri.toString() + " - Code: " + responseCode);

            if (responseCode == 200) {
                if (!responseBody.isEmpty() && responseType.equals("application/json;charset=UTF-8")) {
                    responseJson = JacksonUtils.getObjectMapper().readValue(responseBody, ObjectNode.class);
                    if (responseJson != null && (value = responseJson.get("message")) != null && value.isTextual() && !value.asText().isEmpty()) {
                        Application.getInstance().getGUILog().log("[SERVER MESSAGE] " + value.asText());
                    }
                    if (responseJson != null && (value = responseJson.get("username")) != null && value.isTextual() && !value.asText().isEmpty()) {
                        return value.asText();
                    }
                }
                return "";
            }
            else {
                Application.getInstance().getGUILog().log("[ERROR] Operation failed due to server error. Server code: " + responseCode);
                // big flashing red letters that the action failed, or as close as we're going to get
                Utils.showPopupMessage("Action failed. See notification window for details.");
                // throw is done last, after printing the error and any messages that might have been returned
                throw new ServerException(responseBody, responseCode);
            }
        }
    }

    /**
     * @param project
     * @return
     * @throws IllegalStateException
     */
    public static String getServerUrl(Project project) throws IllegalStateException {
        String urlString = null;
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
        else {
            Utils.showPopupMessage("Your project root element doesn't have ModelManagementSystem Stereotype!");
            return null;
        }
        if ((urlString == null || urlString.isEmpty())) {
            Utils.showPopupMessage("Your project root element doesn't have ModelManagementSystem MMS URL stereotype property set!");
            if (MDUtils.isDeveloperMode()) {
                urlString = JOptionPane.showInputDialog("[DEVELOPER MODE] Enter the server URL:", developerUrl);
                developerUrl = urlString;
            }
        }
        if (urlString == null || urlString.isEmpty()) {
            return null;
        }
        return urlString.trim();
    }

    public static String getMmsOrg(Project project)
            throws IOException, URISyntaxException, ServerException {
        URIBuilder uriBuilder = getServiceProjectsUri(project);
        File responseFile = sendMMSRequest(project, buildRequest(HttpRequestType.GET, uriBuilder));
        try (JsonParser responseParser = JacksonUtils.getJsonFactory().createParser(responseFile)) {
            ObjectNode response = JacksonUtils.parseJsonObject(responseParser);
            JsonNode arrayNode;
            if (((arrayNode = response.get("projects")) != null) && arrayNode.isArray()) {
                JsonNode value;
                for (JsonNode projectNode : arrayNode) {
                    if (((value = projectNode.get(MDKConstants.ID_KEY)) != null) && value.isTextual() && value.asText().equals(Converters.getIProjectToIdConverter().apply(project.getPrimaryProject()))
                            && ((value = projectNode.get(MDKConstants.ORG_ID_KEY)) != null) && value.isTextual() && !value.asText().isEmpty()) {
                        return value.asText();
                    }
                }
            }
        }
        return null;
    }

    public static String getUri(Project project)
            throws IOException, URISyntaxException, ServerException {
        URIBuilder uriBuilder = getServiceProjectsUri(project);
        File responseFile = sendMMSRequest(project, buildRequest(HttpRequestType.GET, uriBuilder));
        try (JsonParser responseParser = JacksonUtils.getJsonFactory().createParser(responseFile)) {
            ObjectNode response = JacksonUtils.parseJsonObject(responseParser);
            JsonNode arrayNode;
            if (((arrayNode = response.get("projects")) != null) && arrayNode.isArray()) {
                JsonNode value;
                for (JsonNode projectNode : arrayNode) {
                    if (((value = projectNode.get(MDKConstants.ID_KEY)) != null) && value.isTextual() && value.asText().equals(Converters.getIProjectToIdConverter().apply(project.getPrimaryProject()))
                            && ((value = projectNode.get(MDKConstants.TWC_URI_KEY)) != null) && value.isTextual() && !value.asText().isEmpty()) {
                        return value.asText();
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
        String urlString = getServerUrl(project);
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

        uri.setPath("/alfresco/service");
        if (TicketUtils.isTicketSet(project)) {
            uri.setParameter("alf_ticket", TicketUtils.getTicket(project));
        }
        return uri;

    }

    /**
     * Returns a URIBuilder object with a path = "/alfresco/service/orgs"
     *
     * @param project The project to gather the mms url and site name information from
     * @return URIBuilder
     */
    public static URIBuilder getServiceOrgsUri(Project project) {
        URIBuilder siteUri = getServiceUri(project);
        if (siteUri == null) {
            return null;
        }
        siteUri.setPath(siteUri.getPath() + "/orgs");
        return siteUri;
    }

    /**
     * Returns a URIBuilder object with a path = "/alfresco/service/projects/{$PROJECT_ID}"
     *
     * @param project The project to gather the mms url and site name information from
     * @return URIBuilder
     */
    public static URIBuilder getServiceProjectsUri(Project project) {
        URIBuilder projectUri = getServiceUri(project);
        if (projectUri == null) {
            return null;
        }
        projectUri.setPath(projectUri.getPath() + "/projects");
        return projectUri;
    }

    /**
     * Returns a URIBuilder object with a path = "/alfresco/service/projects/{$PROJECT_ID}/refs/{$WORKSPACE_ID}"
     *
     * @param project The project to gather the mms url and site name information from
     * @return URIBuilder
     */
    public static URIBuilder getServiceProjectsRefsUri(Project project) {
        URIBuilder refsUri = getServiceProjectsUri(project);
        if (refsUri == null) {
            return null;
        }
        refsUri.setPath(refsUri.getPath() + "/" + Converters.getIProjectToIdConverter().apply(project.getPrimaryProject()) + "/refs");
        return refsUri;
    }

    /**
     * Returns a URIBuilder object with a path = "/alfresco/service/projects/{$PROJECT_ID}/refs/{$WORKSPACE_ID}/elements/${ELEMENT_ID}"
     * if element is not null
     *
     * @param project The project to gather the mms url and site name information from
     * @return URIBuilder
     */
    public static URIBuilder getServiceProjectsRefsElementsUri(Project project) {
        URIBuilder elementUri = getServiceProjectsRefsUri(project);
        if (elementUri == null) {
            return null;
        }
        // TODO review MDUtils.getWorkspace() to make sure it's returning the appropriate thing for branches
        elementUri.setPath(elementUri.getPath() + "/" + MDUtils.getWorkspace(project) + "/elements");
        return elementUri;
    }

    public static String getDefaultSiteName(IProject iProject) {
        String name = iProject.getName().trim().replaceAll("\\W+", "-");
        if (name.endsWith("-")) {
            name = name.substring(0, name.length() - 1);
        }
        return name;
    }

    public static ObjectNode getProjectObjectNode(Project project) {
        return getProjectObjectNode(project.getPrimaryProject());
    }

    public static ObjectNode getProjectObjectNode(IProject iProject) {
        ObjectNode projectObjectNode = JacksonUtils.getObjectMapper().createObjectNode();
        projectObjectNode.put(MDKConstants.TYPE_KEY, "Project");
        projectObjectNode.put(MDKConstants.NAME_KEY, iProject.getName());
        projectObjectNode.put(MDKConstants.ID_KEY, Converters.getIProjectToIdConverter().apply(iProject));
        String resourceId = "";
        if (ProjectUtilities.getProject(iProject).isRemote()) {
            resourceId = ProjectUtilities.getResourceID(iProject.getLocationURI());
        }
        projectObjectNode.put(MDKConstants.TWC_ID_KEY, resourceId);
        String categoryId = "";
        if (ProjectUtilities.getProject(iProject).getPrimaryProject() == iProject && !resourceId.isEmpty()) {
            categoryId = EsiUtils.getCategoryID(resourceId);
        }
        projectObjectNode.put(MDKConstants.CATEGORY_ID_KEY, categoryId);
        projectObjectNode.put(MDKConstants.TWC_URI_KEY, iProject.getProjectDescriptor().getLocationUri().toString());
        return projectObjectNode;
    }

}