/*
 * Copyright 2015, 2016 Ether.Camp Inc. (US)
 * This file is part of Ethereum Harmony.
 *
 * Ethereum Harmony is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Ethereum Harmony is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Ethereum Harmony.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ethercamp.harmony.jsonrpc;

import com.ethercamp.harmony.Application;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mashape.unirest.request.body.RequestBodyEntity;
import junit.framework.Assert;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ethereum.config.SystemProperties;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.WebIntegrationTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Stan Reshetnyk on 17.08.16.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = {Application.class})
//@IntegrationTest("server.port:8080")
@Slf4j
@WebIntegrationTest({"server.port=9999", "management.port=0"})
public class JsonRpcIntegrationTest implements ApplicationContextAware {

    static {
        SystemProperties.getDefault().setBlockchainConfig(new TestApplication.OldFrontierBCConfig());
        // rpc.json genesis created from bcRPC_API_Test.json
        SystemProperties.getDefault().overrideParams(
                "genesis", "rpc.json",
                "database.dir", "no-dir");
    }

    @Value("${server.port}")
    int port;

    String serverUrl;

    final ObjectMapper om = new ObjectMapper();

    @Before
    public void setUp() throws IOException {
        serverUrl = "http://localhost:" + port + "/rpc";
    }

    @Test
    public void eth_getCompilers() throws IOException, UnirestException {
        HttpResponse<String> response = sendRequest("eth_getCompilers", new String[]{});

        assertResult(response, Arrays.asList("solidity"));
    }



    private void assertResult(HttpResponse<String> response, Object result) throws IOException {
        response.getBody();

        Assert.assertEquals(200, response.getCode());

        Map actual = om.readValue(response.getBody(), Map.class);
        Assert.assertEquals(
                makeMapBody(result),
                actual);

        log.info(response.getBody().toString());
    }

    private HttpResponse<String> sendRequest(String method, Object[] params) throws UnirestException, JsonProcessingException {
        Map<String, Object> map = new HashMap<>();
        map.put("id", 73);
        map.put("jsonrpc", "2.0");
        map.put("method", method);
        map.put("params", params);

        return Unirest
                .post(serverUrl)
                .header("Content-Type", "application/json")
                .body(om.writeValueAsString(map).toString())
                .asString();
    }

    private Map<String, Object> makeMapBody(Object result) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", 73);
        map.put("jsonrpc", "2.0");
        map.put("result", result);
        return map;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        try {
            TestApplication.processAfterContext(applicationContext);
        } catch (IOException e) {
            throw new RuntimeException("Problem configuring blockchain for test.");
        }
    }
}