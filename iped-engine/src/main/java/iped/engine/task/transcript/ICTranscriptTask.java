package iped.engine.task.transcript;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import iped.engine.config.ConfigurationManager;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.X509Certificate;
import javax.net.ssl.*;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ICTranscriptTask extends AbstractTranscriptTask {

    private static Logger LOGGER = LoggerFactory.getLogger(ICTranscriptTask.class);

    private static String language = "Portuguese";

    private static String modelsize = "fs:medium";

    private static String serverAddress = "";

    private static Semaphore maxConcurrentRequests;

    private static String AUTHORIZATION_KEY = "";

   
    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {

        super.init(configurationManager);

        if (!transcriptConfig.isEnabled()) {
            return;
        }
        
        if (maxConcurrentRequests == null) {
            maxConcurrentRequests = new Semaphore(transcriptConfig.getMaxConcurrentRequests());
        }
        if (!transcriptConfig.getLanguages().isEmpty()) {
            language = (transcriptConfig.getLanguages()).get(0);
        }
        if (transcriptConfig.getWhisperModel() != null) {
            modelsize = transcriptConfig.getWhisperModel();
        }
        if (transcriptConfig.getRemoteService() != null) {
            serverAddress = transcriptConfig.getRemoteService();
        }
        HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        ignoreCertificateValidationForHost(serverAddress);


    }

    @Override
    public void finish() throws Exception {
        super.finish();
    }

    private static void ignoreCertificateValidationForHost(String allowedHost) throws Exception {
        TrustManager[] trustManagers = new TrustManager[]{
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                }

                public void checkServerTrusted(X509Certificate[] certs, String authType)  {
                    
                }
            }
        };
        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, trustManagers, new java.security.SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

        // Configurar o verificador de nome de host
        HostnameVerifier hostnameVerifier = (hostname, session) -> hostname.equals(allowedHost);
        HttpsURLConnection.setDefaultHostnameVerifier(hostnameVerifier);
    }

    // Classe para mapear o JSON recebido
    public static class ResponseObject {
        private String success;
        private String content;
        private String display;
        private String message;
        private String error;
        private String duration;

        public String getDuration() {
            return duration;
        }

        public void setDuration(String duration) {
            this.duration = duration;
        }

        // Getters e setters
        public String getSuccess() {
            return success;
        }

        public void setSuccess(String success) {
            this.success = success;
        }
        // Getters e setters
        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
        // Getters e setters
        public String getDisplay() {
            return display;
        }

        public void setDisplay(String display) {
            this.display = display;
        }
        // Getters e setters
        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
        // Getters e setters
        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }

    }

    @Override
    protected TextAndScore transcribeAudio(File tmpFile) throws Exception {

        int tries = 0;
        AtomicBoolean ok = new AtomicBoolean();
        TextAndScore textAndScore = null;
        
        while (!ok.get() && ++tries <= 3) {
           
            maxConcurrentRequests.acquire();
            try{
                String boundary = "----WebKitFormBoundary7MA4YWxkTrZu0gW";
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();

                try (DataOutputStream dos = new DataOutputStream(buffer)) {

                    // Adicionar variáveis POST
                    dos.writeBytes("--" + boundary + "\r\n");
                    dos.writeBytes("Content-Disposition: form-data; name=\"model_size\"\r\n\r\n");
                    dos.writeBytes(modelsize + "\r\n");
                    dos.writeBytes("--" + boundary + "\r\n");
                    dos.writeBytes("Content-Disposition: form-data; name=\"language\"\r\n\r\n");
                    dos.writeBytes(language + "\r\n");
                    dos.writeBytes("--" + boundary + "\r\n");
                    dos.writeBytes("Content-Disposition: form-data; name=\"file_type\"\r\n\r\n");
                    dos.writeBytes("TXT\r\n");
                    dos.writeBytes("--" + boundary + "\r\n");
                    dos.writeBytes("Content-Disposition: form-data; name=\"filename\"\r\n\r\n");
                    dos.writeBytes("file_name.media\r\n");

            
                    // Adicionar arquivo binário
                    dos.writeBytes("--" + boundary + "\r\n");
                    dos.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"file_name.media\"\r\n");
                    dos.writeBytes("Content-Type: application/octet-stream\r\n\r\n");
                    
                    try (FileInputStream fis = new FileInputStream(tmpFile)) {
                        byte[] bufferFile = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = fis.read(bufferFile)) != -1) {
                            dos.write(bufferFile, 0, bytesRead);
                        }
                    }
    
                    dos.writeBytes("\r\n");
        
                    // Fechar boundary
                    dos.writeBytes("--" + boundary + "--\r\n");
                }  
                
                // Obter o conteúdo da requisição como bytes
                byte[] requestBody = buffer.toByteArray();

                // Configurar a conexão HTTPS
                URL url = new URL("https://"+serverAddress+"/audio2text/audio2text_query/");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setDoOutput(true);
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Host", serverAddress);
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary="+boundary);
                connection.setRequestProperty("User-Agent", "iped");
                connection.setRequestProperty("Authorization", "Bearer " + AUTHORIZATION_KEY);
                connection.setRequestProperty("Accept", "*/*");
                connection.setRequestProperty("Content-Length", String.valueOf(requestBody.length));
                // Enviar os dados
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(requestBody);
                }
                
                
                InputStream is = connection.getInputStream();
                ObjectMapper objectMapper = new ObjectMapper();
                ResponseObject responseObject = objectMapper.readValue(is, ResponseObject.class);
               
                textAndScore = new TextAndScore();
                textAndScore.text = "<<Automatic Transcription:"+responseObject.getDisplay()+"/>>";
                textAndScore.score = 0D;
                
                ok.set(true);

    
            } catch (Exception ex) {
                ok.set(false);
                LOGGER.error("Error transcribing {} {}", evidence.getPath(), ex.toString());
                LOGGER.warn("", ex);
                

            } finally {
                maxConcurrentRequests.release();
            }
        }

        return textAndScore;

    }

   

}
