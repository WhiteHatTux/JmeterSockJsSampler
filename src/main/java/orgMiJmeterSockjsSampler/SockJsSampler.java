package orgMiJmeterSockjsSampler;

import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient;
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.tomcat.websocket.Constants;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSessionHandler;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.RestTemplateXhrTransport;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.SockJsUrlInfo;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;
import org.springframework.web.socket.sockjs.frame.Jackson2SockJsMessageCodec;
import org.springframework.web.socket.sockjs.transport.TransportType;

public class SockJsSampler extends AbstractJavaSamplerClient implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Jackson2SockJsMessageCodec CODEC = new Jackson2SockJsMessageCodec();

	public static final String DEFAULT_ENCODING = "UTF-8";
	public static final String TRANSPORT = "transport";
	public static final String WEBSOCKET_TRANSPORT = "websocket";
	public static final String XHR_STREAMING_TRANSPORT = "xhr-streaming";
	public static final String HOST = "host";
	public static final String PATH = "path";
	public static final String CONNECTION_TIME = "connectionTime";
	public static final String RESPONSE_BUFFER_TIME = "responseBufferTime";
	public static final String CONNECTION_HEADERS_LOGIN = "connectionHeadersLogin";
	public static final String CONNECTION_HEADERS_PASSCODE = "connectionHeadersPasscode";
	public static final String CONNECTION_HEADERS_HOST = "connectionHeadersHost";
	public static final String CONNECTION_HEADERS_ACCEPT_VERSION = "connectionHeadersAcceptVersion";
	public static final String CONNECTION_HEADERS_HEARTBEAT = "connectionHeadersHeartbeat";
	public static final String CONNECTION_HEADERS_CUSTOM = "connectionHeadersCustom";
	public static final String SUBSCRIBE_HEADERS_ID = "subscribeHeadersId";
	public static final String SUBSCRIBE_HEADERS_DESTINATION = "subscribeHeadersDestination";
    public static final String MESSAGE_DESTINATION = "messageDestination";
    public static final String MESSAGE_BODY = "messageBody";


	@Override
    public void setupTest(JavaSamplerContext context){
		// TODO Auto-generated method stub
		super.setupTest(context);
    }

	// set up default arguments for the JMeter GUI
    @Override
    // @TODO: remove Passwords and so on 
    public Arguments getDefaultParameters() {
        Arguments defaultParameters = new Arguments();
        defaultParameters.addArgument(TRANSPORT, XHR_STREAMING_TRANSPORT, "", "Choose a transport-type: 'websocket' or 'xhr-streaming'");
        defaultParameters.addArgument(HOST, "https://[xxx].[xxx]");
        defaultParameters.addArgument(PATH, "/[xxx]/");
        defaultParameters.addArgument(CONNECTION_TIME, "[30000]");
        defaultParameters.addArgument(RESPONSE_BUFFER_TIME, "[30000]");
        defaultParameters.addArgument(CONNECTION_HEADERS_LOGIN, "login:[xxx]");
        defaultParameters.addArgument(CONNECTION_HEADERS_PASSCODE, "passcode:[xxx]");
        defaultParameters.addArgument(CONNECTION_HEADERS_HOST, "host:[xxx]");
		defaultParameters.addArgument(CONNECTION_HEADERS_ACCEPT_VERSION, "accept-version:1.1,1.0");
		defaultParameters.addArgument(CONNECTION_HEADERS_HEARTBEAT, "heart-beat:0,0");
		defaultParameters.addArgument(CONNECTION_HEADERS_CUSTOM, "cookie:whatever;anyOtherHeaderName:headerValue");
        defaultParameters.addArgument(MESSAGE_DESTINATION, "/app/sendMessage");
        defaultParameters.addArgument(MESSAGE_BODY, "{\"receiver\":\"otherUser\",\"content\":\"Hi otherUser\"}");
        defaultParameters.addArgument(SUBSCRIBE_HEADERS_ID, "id:sub-[x]");
        defaultParameters.addArgument(SUBSCRIBE_HEADERS_DESTINATION, "destination:/exchange/[exchange_name]/[queue_name]");

        return defaultParameters;
    }

    @Override
    public SampleResult runTest(JavaSamplerContext context) {
		SampleResult sampleResult = new SampleResult();
		// record the start time of a sample
		sampleResult.sampleStart();

		ResponseMessage responseMessage = new ResponseMessage();

        try {
        	switch (context.getParameter(TRANSPORT)) {
        		case WEBSOCKET_TRANSPORT:
        			this.createWebsocketConnection(context, responseMessage);
        			break;
        		case XHR_STREAMING_TRANSPORT:
        			this.createXhrStreamingConnection(context, responseMessage);
        			break;
        		default:
        			this.createWebsocketConnection(context, responseMessage);
    				break;
        	}

        	// Record the end time of a sample and calculate the elapsed time
            sampleResult.sampleEnd();
            sampleResult.setSuccessful(true);
            sampleResult.setResponseMessage(responseMessage.getMessage());
            sampleResult.setResponseCodeOK();
        } catch (Exception e) {
        	// Record the end time of a sample and calculate the elapsed time
        	sampleResult.sampleEnd();
            sampleResult.setSuccessful(false);
            sampleResult.setResponseMessage(responseMessage.getMessage());

            // get stack trace as a String to return as document data
            java.io.StringWriter stringWriter = new java.io.StringWriter();
            e.printStackTrace(new java.io.PrintWriter(stringWriter));
            sampleResult.setResponseData(stringWriter.toString(), null);
            sampleResult.setDataType(org.apache.jmeter.samplers.SampleResult.TEXT);
            sampleResult.setResponseCode("500");
        }

        return sampleResult;
    }

    @Override
    public void teardownTest(JavaSamplerContext context) {
        // TODO Auto-generated method stub
    	super.teardownTest(context);
    }

    // Websocket test
 	public void createWebsocketConnection(JavaSamplerContext context, ResponseMessage responseMessage) throws Exception {
 		StandardWebSocketClient simpleWebSocketClient = new StandardWebSocketClient();
		// set up a TrustManager that trusts everything
		SSLContext sslContext = SSLContext.getInstance("TLS");
		sslContext.init(null, new TrustManager[] { new BlindTrustManager() }, null);
		Map<String, Object> userProperties = new HashMap<>();
		userProperties.put(Constants.SSL_CONTEXT_PROPERTY, sslContext);
		simpleWebSocketClient.setUserProperties(userProperties);

		List<Transport> transports = new ArrayList<>(1);
     	transports.add(new WebSocketTransport(simpleWebSocketClient));

 		SockJsClient sockJsClient = new SockJsClient(transports);
 		WebSocketStompClient stompClient = new WebSocketStompClient(sockJsClient);
 		stompClient.setMessageConverter(new StringMessageConverter());

 		URI stompUrlEndpoint = new URI(context.getParameter(HOST) + context.getParameter(PATH));
 		StompSessionHandler sessionHandler = new SockJsWebsocketStompSessionHandler(
			this.getSubscribeHeaders(context),
			context.getLongParameter(CONNECTION_TIME),
			context.getLongParameter(RESPONSE_BUFFER_TIME),
			responseMessage
		);

 		WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
 		StompHeaders connectHeaders = new StompHeaders();
 		String connectionHeadersString = this.getConnectionsHeaders(context);
 		String[] splitHeaders = connectionHeadersString.split("\n");

 		for (int i = 0; i < splitHeaders.length; i++) {
 			int key = 0;
 			int value = 1;
 			String[] headerParameter = splitHeaders[i].split(":");

 			connectHeaders.add(headerParameter[key], headerParameter[value]);
 		}

 		String startMessage = "\n[Execution Flow]"
 						    + "\n - Opening new connection"
 							+ "\n - Using response message pattern \"a[\"CONNECTED\""
 							+ "\n - Using response message pattern \"a[\"MESSAGE\\nsubscription:sub-0\""
 							+ "\n - Using disconnect pattern \"\"";

 		responseMessage.addMessage(startMessage);

 		stompClient.connect(stompUrlEndpoint.toString(), handshakeHeaders, connectHeaders, sessionHandler, new Object[0]);

 		// wait some time till killing the stomp connection
 		Thread.sleep(context.getLongParameter(CONNECTION_TIME) + context.getLongParameter(RESPONSE_BUFFER_TIME));
 		stompClient.stop();

 		String messageVariables = "\n[Variables]"
 								+ "\n" + " - Message count: " + responseMessage.getMessageCounter();

 		responseMessage.addMessage(messageVariables);

 		String messageProblems = "\n[Problems]"
								+ "\n" + responseMessage.getProblems();

 		responseMessage.addMessage(messageProblems);
 	}

    // XHR-Streaming test
    public void createXhrStreamingConnection(JavaSamplerContext context, ResponseMessage responseMessage) throws Exception {
 	  RestTemplate restTemplate = new RestTemplate();
 	  RestTemplateXhrTransport transport = new RestTemplateXhrTransport(restTemplate);
 	  transport.setTaskExecutor(new SyncTaskExecutor());

 	  SockJsUrlInfo urlInfo = new SockJsUrlInfo(new URI(context.getParameter(HOST) + context.getParameter(PATH)));
 	  HttpHeaders headers = new HttpHeaders();
 	  SockJsXhrTransportRequest request = new SockJsXhrTransportRequest(
 		  urlInfo,
 		  headers,
 		  headers,
 		  transport,
 		  TransportType.XHR,
 		  CODEC
 	  );
 	  SockJsXhrSessionHandler xhrSessionHandler = new SockJsXhrSessionHandler(
 		  this.getConnectionsHeaders(context),
 		  this.getSubscribeHeaders(context),
 		  this.getMessagesToSend(context),
 		  context.getLongParameter(CONNECTION_TIME),
		  context.getLongParameter(RESPONSE_BUFFER_TIME),
		  responseMessage
 	  );

 	  String startMessage = "\n[Execution Flow]"
					     + "\n - Opening new connection"
						 + "\n - Using response message pattern \"a[\"CONNECTED\""
						 + "\n - Using response message pattern \"a[\"MESSAGE\\nsubscription:sub-0\""
						 + "\n - Using disconnect pattern \"\"";

 	  responseMessage.addMessage(startMessage);


 	  transport.connect(request, xhrSessionHandler);

 	  String messageVariables = "\n[Variables]"
 							  + "\n" + " - Message count: " + responseMessage.getMessageCounter();

 	  responseMessage.addMessage(messageVariables);

 	  String messageProblems = "\n[Problems]"
 						     + "\n" + responseMessage.getProblems();

 	  responseMessage.addMessage(messageProblems);
   }

	private String getMessagesToSend(JavaSamplerContext context) {
    	if (context.getParameter(MESSAGE_DESTINATION) !=null) {
			return String.format(
					"%s\n%s",
					context.getParameter(MESSAGE_DESTINATION),
					context.getParameter(MESSAGE_BODY)
			);
		} else{
    		return null;
		}
	}

	private String getSubscribeHeaders(JavaSamplerContext context) {
    	return String.format(
			"%s\n%s",
			context.getParameter(SUBSCRIBE_HEADERS_ID),
			context.getParameter(SUBSCRIBE_HEADERS_DESTINATION)
		);
    }

	private String getConnectionsHeaders(JavaSamplerContext context) {
		String customHeaders = context.getParameter(CONNECTION_HEADERS_CUSTOM);
		String[] headerArray = customHeaders.split(";");

		final StringBuilder connectionHeaders = new StringBuilder();
		connectionHeaders.append(context.getParameter(CONNECTION_HEADERS_LOGIN))
				.append("\n").append(context.getParameter(CONNECTION_HEADERS_PASSCODE))
				.append("\n").append(context.getParameter(CONNECTION_HEADERS_HOST))
				.append("\n").append(context.getParameter(CONNECTION_HEADERS_ACCEPT_VERSION))
				.append("\n").append(context.getParameter(CONNECTION_HEADERS_HEARTBEAT));

		for (String customHeader : headerArray) {
			connectionHeaders.append("\n").append(customHeader);
		}
		return connectionHeaders.toString();
	}

//	public void createEventsourceConnection(JavaSamplerContext context, ResponseMessage responseMessage) throws Exception {
//	Executor executor = Executors.newSingleThreadExecutor();
//	long reconnectionTimeMillis = 100;
//	URI host = URI.create("https://xxx/stomp");
//	EventSourceClientHandler eventClientSourceHandler = new EventSourceClientHandler() {
//        @Override
//        public void onConnect() {
//        	System.out.println("Connected!!!!!!");
//        }
//
//        @Override
//        public void onError(Throwable t) {
//        	System.out.println("onError!!!!!!");
//            System.out.println("ERROR: " + t);
//        }
//
//		@Override
//		public void onDisconnect() {
//			System.out.println("Disconnected!!!!!!");
//			// TODO Auto-generated method stub
//			
//		}
//
//		@Override
//		public void onMessage(String event, MessageEvent message) {
//			System.out.println("onMessage!!!!!!");
//			// TODO Auto-generated method stub
//		}
//    };		
//    
//	EventSource eventSource = new EventSource(
//		executor, 
//		reconnectionTimeMillis, 
//		host, 
//		eventClientSourceHandler
//	);
//    eventSource.connect();
//} 
}
