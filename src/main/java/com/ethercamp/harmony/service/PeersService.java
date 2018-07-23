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

package com.ethercamp.harmony.service;

import com.ethercamp.harmony.config.WebEnabledCondition;
import com.ethercamp.harmony.model.dto.PeerDTO;
import com.maxmind.geoip.Country;
import com.maxmind.geoip.LookupService;
import lombok.extern.slf4j.Slf4j;
import org.ethereum.facade.Ethereum;
import org.ethereum.listener.EthereumListenerAdapter;
import org.ethereum.net.eth.message.EthMessageCodes;
import org.ethereum.net.message.Message;
import org.ethereum.net.rlpx.Node;
import org.ethereum.net.rlpx.discover.NodeManager;
import org.ethereum.net.rlpx.discover.NodeStatistics;
import org.ethereum.net.server.Channel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.*;

import static java.util.stream.Collectors.*;

/**
 * Service for rendering list of peers and geographic activity in ethereum network.
 *
 * Created by Stan Reshetnyk on 25.07.16.
 */
@Service
@Slf4j(topic = "harmony")
@Conditional(WebEnabledCondition.class)
public class PeersService {

    private Optional<LookupService> lookupService = Optional.empty();

    private final Map<String, Locale> localeMap = new HashMap<>();

    @Autowired
    private NodeManager nodeManager;

    @Autowired
    private ClientMessageService clientMessageService;

    @Autowired
    private Ethereum ethereum;

    @Autowired
    private Environment env;


    @PostConstruct
    private void postConstruct() {
        // gather blocks to calculate hash rate
        ethereum.addListener(new EthereumListenerAdapter() {
            @Override
            public void onRecvMessage(Channel channel, Message message) {
                // notify client about new block
                // using PeerDTO as it already has both country fields
                if (message.getCommand() == EthMessageCodes.NEW_BLOCK) {
                    clientMessageService.sendToTopic("/topic/newBlockFrom", createPeerDTO(
                            channel.getPeerId(),
                            channel.getInetSocketAddress().getHostName(),
                            0l, 0.0,
                            0,
                            true,
                            null,
                            Optional.of(channel.getEthHandler().getBestKnownBlock())
                                    .map(b -> b.getNumber())
                                    .orElse(0L)
                    ));
                }
            }
        });

        createGeoDatabase();
    }

    /**
     * Reads discovered and active peers from ethereum and sends to client.
     */
    @Scheduled(fixedRate = 1500)
    private void doSendPeersInfo() {
        // #1 Read discovered nodes. Usually ~150 nodes
        final List<Node> nodes = nodeManager.getTable()
                .getAllNodes().stream()
                .map(n -> n.getNode())
                .collect(toList());

        // #2 Convert active peers to DTO
        final List<PeerDTO> resultPeers = ethereum.getChannelManager().getActivePeers()
                .stream()
                .map(channel ->
                    createPeerDTO(
                            channel.getPeerId(),
                            channel.getNode().getHost(),
                            channel.getNodeStatistics().lastPongReplyTime.get(),
                            channel.getPeerStats().getAvgLatency(),
                            channel.getNodeStatistics().getReputation(),
                            true,
                            channel.getNodeStatistics(),
                            channel.getEthHandler().getBestKnownBlock().getNumber())
                )
                .collect(toList());

        // #3 Convert discovered peers to DTO and add to result
        nodes.forEach(node -> {
            boolean isPeerAdded = resultPeers.stream()
                    .anyMatch(addedPeer -> addedPeer.getNodeId().equals(node.getHexId()));
            if (!isPeerAdded) {
                NodeStatistics nodeStatistics = nodeManager.getNodeStatistics(node);
                resultPeers.add(createPeerDTO(
                        node.getHexId(),
                        node.getHost(),
                        nodeStatistics.lastPongReplyTime.get(),
                        0.0,
                        nodeStatistics.getReputation(),
                        false,
                        null,
                        0));
            }
        });

        clientMessageService.sendToTopic("/topic/peers", resultPeers);
    }

    private String getPeerDetails(NodeStatistics nodeStatistics, String country, long maxBlockNumber) {
        final String countryRow = "Country: " + country;

        if (nodeStatistics == null || nodeStatistics.getClientId() == null) {
            return countryRow;
        }

        final String delimiter = "\n";
        final String blockNumber = "Block number: #" + NumberFormat.getNumberInstance(Locale.US).format(maxBlockNumber);
        final String clientId = StringUtils.trimWhitespace(nodeStatistics.getClientId());
        final String details = "Details: " + clientId;
        final String supports = "Supported protocols: " + nodeStatistics.capabilities
                .stream()
                .filter(c -> c != null)
                .map(c -> StringUtils.capitalize(c.getName()) + ": " + c.getVersion())
                .collect(joining(", "));

        final String[] array = clientId.split("/");
        if (array.length >= 4) {
            final String type = "Type: " + array[0];
            final String os = "OS: " + StringUtils.capitalize(array[2]);
            final String version = "Version: " + array[3];

            return String.join(delimiter, type, os, version, countryRow, "", details, supports, blockNumber);
        } else {
            return String.join(delimiter, countryRow, details, supports, blockNumber);
        }
    }

    private PeerDTO createPeerDTO(String peerId, String ip, long lastPing, double avgLatency, int reputation,
                                  boolean isActive, NodeStatistics nodeStatistics, long maxBlockNumber) {
        // code or ""

        final Optional<Country> country = lookupService.map(service -> service.getCountry(ip));
        final String country2Code = country
                .map(c -> c.getCode())
                .orElse("");

        // code or ""
        final String country3Code = iso2CountryCodeToIso3CountryCode(country2Code);

        return new PeerDTO(
                peerId,
                ip,
                country3Code,
                country2Code,
                lastPing,
                avgLatency,
                reputation,
                isActive,
                getPeerDetails(nodeStatistics, country.map(Country::getName).orElse("Unknown location"), maxBlockNumber));
    }

    /**
     * Create MaxMind lookup service to find country by IP.
     * IPv6 is not used.
     */
    private void createGeoDatabase() {
        final String[] countries = Locale.getISOCountries();
        final Optional<String> dbFilePath = Optional.ofNullable(env.getProperty("maxmind.file"));

        for (String country : countries) {
            Locale locale = new Locale("", country);
            localeMap.put(locale.getISO3Country().toUpperCase(), locale);
        }
        lookupService = dbFilePath
                .flatMap(path -> {
                    try {
                        return Optional.ofNullable(new LookupService(
                                path,
                                LookupService.GEOIP_MEMORY_CACHE | LookupService.GEOIP_CHECK_CACHE));
                    } catch(IOException e) {
                        log.error("Problem finding maxmind database at " + path + ". " + e.getMessage());
                        log.error("Wasn't able to create maxmind location service. Country information will not be available.");
                        return Optional.empty();
                    }
                });
    }

    private String iso2CountryCodeToIso3CountryCode(String iso2CountryCode){
        Locale locale = new Locale("", iso2CountryCode);
        try {
            return locale.getISO3Country();
        } catch (MissingResourceException e) {
            // silent
        }
        return "";
    }

}
