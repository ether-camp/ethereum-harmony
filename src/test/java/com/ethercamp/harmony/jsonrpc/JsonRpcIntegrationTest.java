package com.ethercamp.harmony.jsonrpc;

import com.ethercamp.harmony.Application;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.IntegrationTest;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.http.HttpStatus;

/**
 * Created by Stan Reshetnyk on 17.08.16.
 */
//@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = Application.class)
//@WebAppConfiguration
@IntegrationTest("server.port:0")
public class JsonRpcIntegrationTest {

    @Value("${local.server.port}")
    int port;

    @Before
    public void setUp() {
//        RestAssured.port = port;
    }

    @Test
    public void canFetchMickey() {
    }
}