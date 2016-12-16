package com.ethercamp.harmony.service;

import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import javax.net.ssl.SSLContext;
import java.util.Arrays;

/**
 * Snippet for importing real contracts from test.ether.camp to localhost.
 *
 * Created by Stan Reshetnyk on 21.11.16.
 */
@Ignore
public class ImportManyContractsTest {

    @BeforeClass
    public static void beforeClass() throws Exception {
        // ignore https errors
        SSLContext sslcontext = SSLContexts.custom()
                .loadTrustMaterial(null, new TrustSelfSignedStrategy())
                .build();

        SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslcontext);
        CloseableHttpClient httpclient = HttpClients.custom()
                .setSSLSocketFactory(sslsf)
                .build();
        Unirest.setHttpClient(httpclient);
    }

    @Test
    public void importContractsFromSite() throws UnirestException {
        JsonNode result = Unirest.get("https://test-state.ether.camp/api/v1/contracts?page=1&size=200")
                .asJson().getBody();

        JSONArray accounts = result.getObject().getJSONArray("content");

        if (accounts.length() == 0) {
            System.err.println("No contracts left");
        }

        for (int i = 0; i< accounts.length(); i++) {
            System.out.println("Processing item " + i);
            JSONObject account = (JSONObject) accounts.get(i);
            String address = account.getString("address");
            String name = account.getString("name");

            verifyContract(address, name);
        }

        System.out.println("Done");
    }

    @Test
    public void importSpecificContractFromSite() throws Exception {
        Arrays.asList("086e68b8b72f618ec22c6996f63505975a643fd2").stream()
                .forEach(a -> verifyContract(a, null));

        System.out.println("Done");
    }

    private void verifyContract(String address, String name) {
        try {
            String accountUrl = "https://test-state.ether.camp/api/v1/accounts/" + address + "/contract";
            JsonNode accountResult = Unirest.get(accountUrl)
                    .asJson().getBody();

            String source = accountResult.getObject().getString("source");

            String harmonyUrl = "http://localhost:8080/contracts/add";
            JSONObject sendData = new JSONObject();
            sendData.put("address", address);
            sendData.put("sourceCode", source);
            JSONObject importResult = null;

            importResult = Unirest.post(harmonyUrl)
                    .header("Content-Type", "application/json; charset=utf8")
                    .body(sendData.toString()).asJson().getBody().getObject();


            System.out.println("Imported contract " + address + " name: " + name);
            boolean success = importResult.getBoolean("success");
            if (success) {
                System.out.println("Result success");
            } else {
                System.out.println("Result FAULT " + importResult.getString("errorMessage"));
            }
        } catch (UnirestException e) {
            throw new RuntimeException(e);
        }
    }



}
