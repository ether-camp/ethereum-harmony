package com.ethercamp.harmony.service;

import com.ethercamp.harmony.dto.PeerDTO;
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
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for rendering list of peers and geographic activity in ethereum network.
 *
 * Created by Stan Reshetnyk on 25.07.16.
 */
@Service
@Slf4j(topic = "harmony")
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
                            true
                    ));
                }
            }

            @Override
            public void onPeerAddedToSyncPool(Channel peer) {
//                log.info("onPeerAddedToSyncPool peer: " + peer.getPeerId());
            }

            @Override
            public void onPeerDisconnect(String host, long port) {
//                log.info("onPeerDisconnect host:" + host + ", port:" + port);
            }
        });

        createGeoDatabase();
    }

    /**
     * Reads discovered and active peers from ethereum and sends to client.
     */
    @Scheduled(fixedRate = 1500)
    private void doSendPeersInfo() {
        // #1 Read discovered peers. Usually ~150 peers
        final List<Node> nodes = nodeManager.getTable()
                .getAllNodes().stream()
                .map(n -> n.getNode())
                .collect(Collectors.toList());

        // #2 Convert active peers to DTO
        final List<PeerDTO> resultPeers = ethereum.getChannelManager().getActivePeers()
                .stream()
                .map(p -> createPeerDTO(
                        p.getPeerId(),
                        p.getNode().getHost(),
                        p.getNodeStatistics().lastPongReplyTime.get(),
                        p.getPeerStats().getAvgLatency(),
                        p.getNodeStatistics().getReputation(),
                        true))
                .collect(Collectors.toList());

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
                        false
                ));
            }
        });

        clientMessageService.sendToTopic("/topic/peers", resultPeers);
    }

    private PeerDTO createPeerDTO(String peerId, String ip, long lastPing, double avgLatency, int reputation, boolean isActive) {
        // code or ""
        final String country2Code = lookupService
                .map(service -> service.getCountry(ip).getCode())
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
                isActive);
    }

    /**
     * Create MaxMind lookup service to find country by IP.
     * IPv6 is not used.
     */
    private void createGeoDatabase() {
        final String[] countries = Locale.getISOCountries();
        final String dbFilePath = env.getProperty("maxmind.file");

        for (String country : countries) {
            Locale locale = new Locale("", country);
            localeMap.put(locale.getISO3Country().toUpperCase(), locale);
        }
        try {
            lookupService = Optional.of(new LookupService(
                    dbFilePath,
                    LookupService.GEOIP_MEMORY_CACHE | LookupService.GEOIP_CHECK_CACHE));
        } catch (IOException e) {
            log.error("Please download file and put to " + dbFilePath);
            log.error("Wasn't able to create maxmind location service. Country information will not be available.", e);
        }
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
