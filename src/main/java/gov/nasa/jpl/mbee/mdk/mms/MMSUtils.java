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
import gov.nasa.jpl.mbee.mdk.http.HttpDeleteWithBody;
import gov.nasa.jpl.mbee.mdk.http.ServerException;
import gov.nasa.jpl.mbee.mdk.json.JacksonUtils;
import gov.nasa.jpl.mbee.mdk.mms.actions.MMSLogoutAction;
import gov.nasa.jpl.mbee.mdk.options.MDKOptionsGroup;
import gov.nasa.jpl.mbee.mdk.util.MDUtils;
import gov.nasa.jpl.mbee.mdk.util.TaskRunner;
import gov.nasa.jpl.mbee.mdk.util.TicketUtils;
import gov.nasa.jpl.mbee.mdk.util.Utils;
import org.apache.commons.io.IOUtils;
import org.apache.http.Consts;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.EntityBuilder;
import org.apache.http.client.methods.*;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.FileEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.ssl.TrustStrategy;

import javax.net.ssl.SSLContext;
import javax.swing.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

public class MMSUtils {

	public static String serverTrustMethod = "DEFAULT"; //DEFAULT or WINDOWS
    private static final int CHECK_CANCEL_DELAY = 100;
    private static final AtomicReference<Exception> LAST_EXCEPTION = new AtomicReference<>();
    private static final Cache<Project, String> PROFILE_SERVER_CACHE = CacheBuilder.newBuilder().weakKeys().maximumSize(100).expireAfterAccess(10, TimeUnit.MINUTES).build();
    
    public enum HttpRequestType {
        GET, POST, PUT, DELETE
    }

    public enum JsonBlobType {
        ELEMENT_JSON, ELEMENT_ID, ARTIFACT_JSON, ARTIFACT_ID, PROJECT, REF, ORG
    }

    public static AtomicReference<Exception> getLastException() {
        return LAST_EXCEPTION;
    }

    public static ObjectNode getElement(Project project, String elementId, ProgressStatus progressStatus) throws IOException, ServerException, URISyntaxException {
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

    public static File getElementRecursively(Project project, String elementId, int depth, ProgressStatus progressStatus) throws IOException, ServerException, URISyntaxException {
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
    public static File getElements(Project project, Collection<String> elementIds, ProgressStatus progressStatus) throws IOException, ServerException, URISyntaxException {
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
    public static File getElementsRecursively(Project project, Collection<String> elementIds, int depth, ProgressStatus progressStatus) throws ServerException, IOException, URISyntaxException {
        // verify elements
        if (elementIds == null || elementIds.isEmpty()) {
            return null;
        }

        // build uri
        URIBuilder requestUri = getServiceProjectsRefsElementsUri(project);
        if (requestUri == null) {
            return null;
        }
        requestUri.setParameter("depth", java.lang.Integer.toString(depth));

        // create request file
        File sendData = createEntityFile(MMSUtils.class, ContentType.APPLICATION_JSON, elementIds, JsonBlobType.ELEMENT_ID);

        //do cancellable request if progressStatus exists
        return sendMMSRequest(project, MMSUtils.buildRequest(MMSUtils.HttpRequestType.PUT, requestUri, sendData, ContentType.APPLICATION_JSON), progressStatus);
    }

    public static File getArtifacts(Project project, Collection<String> artifactIds, ProgressStatus progressStatus) throws ServerException, IOException, URISyntaxException {
        if (artifactIds == null || artifactIds.isEmpty()) {
            return null;
        }
        URIBuilder requestUri = getServiceProjectsRefsArtifactsUri(project);
        if (requestUri == null) {
            return null;
        }
        File sendData = createEntityFile(MMSUtils.class, ContentType.APPLICATION_JSON, artifactIds, JsonBlobType.ARTIFACT_ID);
        return sendMMSRequest(project, MMSUtils.buildRequest(MMSUtils.HttpRequestType.PUT, requestUri, sendData, ContentType.APPLICATION_JSON), progressStatus);
    }

    public static String getCredentialsTicket(Project project, String username, String password, ProgressStatus progressStatus) throws ServerException, IOException, URISyntaxException {
        return getCredentialsTicket(project, null, username, password, progressStatus);
    }

    public static String getCredentialsTicket(String baseUrl, String username, String password, ProgressStatus progressStatus) throws ServerException, IOException, URISyntaxException {
        return getCredentialsTicket(null, baseUrl, username, password, progressStatus);
    }

    private static String getCredentialsTicket(Project project, String baseUrl, String username, String password, ProgressStatus progressStatus) throws ServerException, IOException, URISyntaxException {
        URIBuilder requestUri = MMSUtils.getServiceUri(project, baseUrl);
        if (requestUri == null) {
            return null;
        }
        requestUri.setPath(requestUri.getPath() + "/api/login");
        requestUri.clearParameters();

        //build request
        URI requestDest = requestUri.build();
        HttpRequestBase request = new HttpPost(requestDest);

        request.addHeader("Content-Type", "application/json");
        request.addHeader("charset", (Consts.UTF_8).displayName());

        ObjectNode credentials = JacksonUtils.getObjectMapper().createObjectNode();
        credentials.put("username", username);
        credentials.put("password", password);
        String data = JacksonUtils.getObjectMapper().writeValueAsString(credentials);
        ((HttpEntityEnclosingRequest) request).setEntity(new StringEntity(data, ContentType.APPLICATION_JSON));

        // do request
        ObjectNode responseJson = JacksonUtils.getObjectMapper().createObjectNode();
        sendMMSRequest(project, request, progressStatus, responseJson);
        JsonNode value;
        if (responseJson != null && (value = responseJson.get("data")) != null && (value = value.get("ticket")) != null && value.isTextual()) {
            return value.asText();
        }
        return null;
    }

    public static String validateCredentialsTicket(Project project, String ticket, ProgressStatus progressStatus) throws ServerException, IOException, URISyntaxException {
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
        ObjectNode responseJson = JacksonUtils.getObjectMapper().createObjectNode();
        sendMMSRequest(project, request, progressStatus, responseJson);

        // parse response
        JsonNode value;
        if (responseJson != null && (value = responseJson.get("username")) != null && value.isTextual() && !value.asText().isEmpty()) {
            return value.asText();
        }
        return "";
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
    public static HttpRequestBase buildImageRequest(URIBuilder requestUri, File sendFile) throws IOException, URISyntaxException {
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
    public static HttpRequestBase buildRequest(HttpRequestType type, URIBuilder requestUri, File sendData, ContentType contentType) throws IOException, URISyntaxException {
        // build specified request type
        // assume that any request can have a body, and just build the appropriate one
        URI requestDest = requestUri.build();
        final HttpRequestBase request;
        // bulk GETs are not supported in MMS, but bulk PUTs are. checking and and throwing error here in case
        if (type == HttpRequestType.GET && sendData != null) {
            throw new IOException("GETs with body are not supported");
        }
        switch (type) {
            case DELETE:
                request = new HttpDeleteWithBody(requestDest);
                break;
            case GET:
            default:
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
    public static HttpRequestBase buildRequest(HttpRequestType type, URIBuilder requestUri) throws IOException, URISyntaxException {
        return buildRequest(type, requestUri, null, null);
    }

    public static File createEntityFile(Class<?> clazz, ContentType contentType, Collection<?> nodes, JsonBlobType jsonBlobType) throws IOException {
        File requestFile = File.createTempFile(clazz.getSimpleName() + "-" + contentType.getMimeType().replace('/', '-') + "-", null);
        if (MDKOptionsGroup.getMDKOptions().isLogJson()) {
            System.out.println("[INFO] Request Body: " + requestFile.getPath());
            Application.getInstance().getGUILog().log("[INFO] Request Body: " + requestFile.getPath());
        }
        else {
            requestFile.deleteOnExit();
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
            jsonGenerator.writeArrayFieldStart(arrayName);
            for (Object node : nodes) {
                if (node instanceof ObjectNode && jsonBlobType == JsonBlobType.ELEMENT_JSON || jsonBlobType == JsonBlobType.ORG || jsonBlobType == JsonBlobType.PROJECT || jsonBlobType == JsonBlobType.REF) {
                    jsonGenerator.writeObject(node);
                }
                else if (node instanceof String && jsonBlobType == JsonBlobType.ELEMENT_ID || jsonBlobType == JsonBlobType.ARTIFACT_ID) {
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
            jsonGenerator.writeStringField("mdkVersion", MDKPlugin.getVersion());
            jsonGenerator.writeEndObject();
        }

        return requestFile;
    }
    


    public static CloseableHttpClient createTrustAllHttpClient() throws KeyManagementException, NoSuchAlgorithmException, KeyStoreException {
    	SSLContext sslcontext = SSLContexts.custom()
    			.loadTrustMaterial(null,new TrustStrategy() {
					@Override
					public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
						return true;
					}
    			})
    			.build();
    	SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslcontext);
    	CloseableHttpClient httpclient = HttpClients.custom()
    			.setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build())
    			.setSSLSocketFactory(sslsf)
    			.build();
    	return httpclient;
	}
    
    public static CloseableHttpClient createWindowsHttpClient() throws NoSuchAlgorithmException, CertificateException, IOException, KeyManagementException, UnrecoverableKeyException, KeyStoreException {
    	KeyStore keystoreWinRoot = KeyStore.getInstance("Windows-ROOT");
    	KeyStore keystoreWinMy = KeyStore.getInstance("Windows-MY");
    	keystoreWinRoot.load(null,null);
    	keystoreWinMy.load(null,null);
    	SSLContext sslcontext = SSLContexts.custom()
    			.loadKeyMaterial(keystoreWinMy,null)
    			.loadTrustMaterial(keystoreWinRoot,null)
    			.build();
    	SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslcontext);
    	CloseableHttpClient httpclient = HttpClients.custom()
    			.setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build())
    			.setSSLSocketFactory(sslsf)
    			.build();
    	return httpclient;
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
    public static File sendMMSRequest(Project project, HttpRequestBase request, ProgressStatus progressStatus, final ObjectNode responseJson) throws IOException, ServerException, URISyntaxException {
        final File responseFile = (responseJson == null ? File.createTempFile("Response-", null) : null);
        final AtomicReference<String> responseBody = new AtomicReference<>();
        final AtomicReference<Integer> responseCode = new AtomicReference<>();

        String requestSummary = "[INFO] MMS Request [" + request.getMethod() + ":" +serverTrustMethod + "] " + request.getURI().toString();
        System.out.println(requestSummary);
        if (MDUtils.isDeveloperMode()) {
            Application.getInstance().getGUILog().log(requestSummary);
        }

        // create client, execute request, parse response, store in thread safe buffer to return as string later
        // client, response, and reader are all auto closed after block
        if (progressStatus == null) {
        	if(serverTrustMethod.equals("WINDOWS")) {
	            try (CloseableHttpClient httpclient = createWindowsHttpClient();
	                 CloseableHttpResponse response = httpclient.execute(request);
	                 InputStream inputStream = response.getEntity().getContent()) {
	                responseCode.set(response.getStatusLine().getStatusCode());
	                String responseSummary = "[INFO] MMS Windows Response [" + request.getMethod() + "]: " + responseCode.get() + " " + request.getURI().toString();
	                System.out.println(responseSummary);
	                if (MDUtils.isDeveloperMode()) {
	                    Application.getInstance().getGUILog().log(responseSummary);
	                }
	                if (inputStream != null) {
	                    responseBody.set(generateMmsOutput(inputStream, responseFile));
	                }
	            } catch (KeyManagementException | UnrecoverableKeyException | NoSuchAlgorithmException
						| CertificateException | KeyStoreException e) {
					e.printStackTrace();
				}
        	}
        	else if(serverTrustMethod.equals("TRUSTALL")) {
        		try (CloseableHttpClient httpclient = createTrustAllHttpClient();
   	                 CloseableHttpResponse response = httpclient.execute(request);
   	                 InputStream inputStream = response.getEntity().getContent()) {
   	                responseCode.set(response.getStatusLine().getStatusCode());
   	                String responseSummary = "[INFO] MMS Windows Response [" + request.getMethod() + "]: " + responseCode.get() + " " + request.getURI().toString();
   	                System.out.println(responseSummary);
   	                if (MDUtils.isDeveloperMode()) {
   	                    Application.getInstance().getGUILog().log(responseSummary);
   	                }
   	                if (inputStream != null) {
   	                    responseBody.set(generateMmsOutput(inputStream, responseFile));
   	                }
   	            } catch (Exception e) {
   					e.printStackTrace();
   				}
        	}
        	else {
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
        }
        else {
            LAST_EXCEPTION.set(null);
            progressStatus.setIndeterminate(true);
            Future<?> future = TaskRunner.runWithProgressStatus(() -> {
            	if(serverTrustMethod.equals("WINDOWS")) {
	                try (CloseableHttpClient httpclient = createWindowsHttpClient();
	                     CloseableHttpResponse response = httpclient.execute(request);
	                     InputStream inputStream = response.getEntity().getContent()) {
	                    responseCode.set(response.getStatusLine().getStatusCode());
	                    if (MDKOptionsGroup.getMDKOptions().isLogJson()) {
	                        System.out.println("[INFO] MMS Response [" + request.getMethod() + ":" +serverTrustMethod+"]: " + responseCode.get() + " " + request.getURI().toString());
	                    }
	                    if (inputStream != null) {
	                        responseBody.set(generateMmsOutput(inputStream, responseFile));
	                    }
	                } catch (Exception e) {
	                    LAST_EXCEPTION.set(e);
	                    e.printStackTrace();
	                }
            	}
            	else if(serverTrustMethod.equals("TRUSTALL")) {
            		try (CloseableHttpClient httpclient = createTrustAllHttpClient();
   	                     CloseableHttpResponse response = httpclient.execute(request);
   	                     InputStream inputStream = response.getEntity().getContent()) {
   	                    responseCode.set(response.getStatusLine().getStatusCode());
   	                    if (MDKOptionsGroup.getMDKOptions().isLogJson()) {
   	                        System.out.println("[INFO] MMS Response [" + request.getMethod() + ":" +serverTrustMethod+"]: " + responseCode.get() + " " + request.getURI().toString());
   	                    }
   	                    if (inputStream != null) {
   	                        responseBody.set(generateMmsOutput(inputStream, responseFile));
   	                    }
   	                } catch (Exception e) {
   	                    LAST_EXCEPTION.set(e);
   	                    e.printStackTrace();
   	                }
            	}
            	else {
            		try (CloseableHttpClient httpclient = HttpClients.createDefault();
   	                     CloseableHttpResponse response = httpclient.execute(request);
   	                     InputStream inputStream = response.getEntity().getContent()) {
   	                    responseCode.set(response.getStatusLine().getStatusCode());
   	                    if (MDKOptionsGroup.getMDKOptions().isLogJson()) {
   	                        System.out.println("[INFO] MMS Response [" + request.getMethod() + ":" +serverTrustMethod+"]: " + responseCode.get() + " " + request.getURI().toString());
   	                    }
   	                    if (inputStream != null) {
   	                        responseBody.set(generateMmsOutput(inputStream, responseFile));
   	                    }
   	                } catch (Exception e) {
   	                    LAST_EXCEPTION.set(e);
   	                    e.printStackTrace();
   	                }
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

	public static File sendMMSRequest(Project project, HttpRequestBase request) throws IOException, ServerException, URISyntaxException {
        return sendMMSRequest(project, request, null, null);
    }

    public static File sendMMSRequest(Project project, HttpRequestBase request, ProgressStatus progressStatus) throws IOException, ServerException, URISyntaxException {
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
                    responseFile.deleteOnExit();
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
        JsonFactory jsonFactory = JacksonUtils.getJsonFactory();
        try (JsonParser jsonParser = jsonFactory.createParser(responseStream)) {
            while (jsonParser.nextFieldName() != null && !jsonParser.nextFieldName().equals("message")) {
                // spin until we find message
            }
            if (jsonParser.getCurrentToken() == JsonToken.FIELD_NAME) {
                jsonParser.nextToken();
                Application.getInstance().getGUILog().log("[SERVER MESSAGE] " + jsonParser.getText());
            }
        } catch (IOException e) {
            Application.getInstance().getGUILog().log("[WARNING] Unable to retrieve messages from server response.");
            throwServerException = true;
        }

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

    public static String getMmsOrg(Project project) throws IOException, URISyntaxException, ServerException {
        URIBuilder uriBuilder = getServiceProjectsUri(project);
        File responseFile = sendMMSRequest(project, buildRequest(HttpRequestType.GET, uriBuilder));
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

        uri.setPath("/alfresco/service");
        if (project != null && TicketUtils.isTicketSet(project)) {
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
        return getServiceOrgsUri(project, null);
    }

    public static URIBuilder getServiceOrgsUri(String baseUrl) {
        return getServiceOrgsUri(null, baseUrl);
    }

    private static URIBuilder getServiceOrgsUri(Project project, String baseUrl) {
        URIBuilder siteUri = getServiceUri(project, baseUrl);
        if (siteUri == null) {
            return null;
        }
        siteUri.setPath(siteUri.getPath() + "/orgs");
        return siteUri;
    }

    /**
     * Returns a URIBuilder object with a path = "/alfresco/service/projects"
     *
     * @param project The project to gather the mms url and site name information from
     * @return URIBuilder
     */
    public static URIBuilder getServiceProjectsUri(Project project) {
        return getServiceProjectsUri(project, null);
    }

    public static URIBuilder getServiceProjectsUri(String baseUrl) {
        return getServiceProjectsUri(null, baseUrl);
    }

    private static URIBuilder getServiceProjectsUri(Project project, String baseUrl) {
        URIBuilder projectUri = getServiceUri(project, baseUrl);
        if (projectUri == null) {
            return null;
        }
        projectUri.setPath(projectUri.getPath() + "/projects");
        return projectUri;
    }

    /**
     * Returns a URIBuilder object with a path = "/alfresco/service/projects/{$PROJECT_ID}/refs"
     *
     * @param project The project to gather the mms url and site name information from
     * @return URIBuilder
     */
    public static URIBuilder getServiceProjectsRefsUri(Project project) {
        return getServiceProjectsRefsUri(project, null, null);
    }

    public static URIBuilder getServiceProjectsRefsUri(String baseUrl, String projectId) {
        return getServiceProjectsRefsUri(null, baseUrl, projectId);
    }

    private static URIBuilder getServiceProjectsRefsUri(Project project, String baseUrl, String projectId) {
        URIBuilder refsUri = getServiceProjectsUri(project, baseUrl);
        if (refsUri == null) {
            return null;
        }
        refsUri.setPath(refsUri.getPath() + "/" + (project == null ? projectId : Converters.getIProjectToIdConverter().apply(project.getPrimaryProject())) + "/refs");
        return refsUri;
    }

    /**
     * Returns a URIBuilder object with a path = "/alfresco/service/projects/{$PROJECT_ID}/refs/{REF_ID}/elements"
     * if element is not null
     *
     * @param project The project to gather the mms url and site name information from
     * @return URIBuilder
     */
    public static URIBuilder getServiceProjectsRefsElementsUri(Project project) {
        URIBuilder elementsUri = getServiceProjectsRefsUri(project);
        if (elementsUri == null) {
            return null;
        }
        elementsUri.setPath(elementsUri.getPath() + "/" + MDUtils.getBranchId(project) + "/elements");
        return elementsUri;
    }

    public static URIBuilder getServiceProjectsRefsArtifactsUri(Project project) {
        URIBuilder artifactsUri = getServiceProjectsRefsUri(project);
        if (artifactsUri == null) {
            return null;
        }
        artifactsUri.setPath(artifactsUri.getPath() + "/" + MDUtils.getBranchId(project) + "/artifacts");
        return artifactsUri;
    }

    public static String getDefaultSiteName(IProject iProject) {
        String name = iProject.getName().trim().replaceAll("\\W+", "-");
        if (name.endsWith("-")) {
            name = name.substring(0, name.length() - 1);
        }
        return name;
    }

}
