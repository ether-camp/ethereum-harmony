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

import com.ethercamp.harmony.jsonrpc.TypeConverter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.ethereum.casper.core.CasperFacade;
import org.ethereum.core.Block;
import org.ethereum.core.BlockSummary;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.Denomination;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.facade.Ethereum;
import org.ethereum.listener.EthereumListenerAdapter;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.FastByteComparisons;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.spongycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.math.BigInteger;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.Queue;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentLinkedQueue;

import static com.ethercamp.harmony.service.BlockchainInfoService.KEEP_LOG_ENTRIES;

/**
 * Everything about Casper contract
 */
@Service
@Slf4j(topic = "harmony")
public class CasperInfoService {

    private static final String PARSE_ERRROR = "<Unable to parse>";

    @Autowired
    CasperFacade casper;

    @Autowired
    Ethereum ethereum;

    @Autowired
    private ClientMessageService clientMessageService;

    private final Queue<String> lastCasperLogs = new ConcurrentLinkedQueue();

    @PostConstruct
    private void postConstruct() {
        /**
         * - Parse block summaries to get casper requests info
         */
        ethereum.addListener(new EthereumListenerAdapter() {
            @Override
            public void onBlock(BlockSummary blockSummary) {
                parseBlockSummary(blockSummary);
            }
        });
    }

    private void parseBlockSummary(BlockSummary summary) {
        if (casper.getAddress() == null || casper.getAddress().length == 0) {
            return;
        }

        summary.getReceipts().forEach(receipt -> {
            if(receipt.getTransaction().getReceiveAddress() != null &&
                    FastByteComparisons.equal(receipt.getTransaction().getReceiveAddress(), casper.getAddress())) {
                parseCasperReceipt(receipt, summary);
            }
        });
    }

    private static DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .withLocale(Locale.UK)
            .withZone(ZoneId.systemDefault());

    private void parseCasperReceipt(final TransactionReceipt receipt, final BlockSummary summary) {
        CallTransaction.Invocation inv = casper.getContract().parseInvocation(receipt.getTransaction().getData());
        StringBuilder msg = new StringBuilder();
        msg.append(formatter.format(Instant.ofEpochSecond(summary.getBlock().getTimestamp())));
        msg.append(" ");
        msg.append(receipt.isSuccessful() ? "SUCCESS " : "FAIL ");
        String parseRes;
        switch (inv.function.name) {
            case "deposit":
                parseRes = parseDepositData(inv.args, receipt);
                break;
            case "logout":
                parseRes = parseLogoutData((byte[]) inv.args[0]);
                break;
            case "withdraw":
                parseRes = parseWithdrawData((BigInteger) inv.args[0], receipt);
                break;
            case "vote":
                parseRes = parseVoteData((byte[]) inv.args[0]);
                break;
            case "initialize_epoch":
                parseRes = parseInitEpoch((BigInteger) inv.args[0], summary.getBlock());
                break;
            default: {
                StringJoiner joiner = new StringJoiner(", ");
                for (int i = 0; i < inv.args.length; ++i) {
                    String arg;
                    if (inv.args[i] instanceof byte[]) {
                        arg = TypeConverter.toJsonHex((byte[]) inv.args[i]);
                    } else {
                        arg = inv.args[i].toString();
                    }
                    joiner.add(arg);
                }
                parseRes = joiner.toString();
            }

        }

        msg.append(String.format("%s(%s)", StringUtils.capitalize(inv.function.name), parseRes));
        addCasperMessage(msg.toString());
    }

    private String parseInitEpoch(BigInteger epoch, Block block) {
        try {
            return String.format("num=%s, block=%s, prevHash=%s",
                    epoch, block.getNumber(), TypeConverter.toJsonHex(block.getParentHash()));
        } catch (Exception ex) {
            return PARSE_ERRROR;
        }
    }

    private String parseDepositData(Object[] args, TransactionReceipt receipt) {
        try {
            String valContract = TypeConverter.toJsonHex((byte[]) args[0]);
            String withdrawalAddress = TypeConverter.toJsonHex((byte[]) args[1]);
            BigInteger weiAmount = ByteUtil.bytesToBigInteger(receipt.getTransaction().getValue());
            double ethAmount = weiAmount.divide(Denomination.FINNEY.value()).longValue() / 1000.0;

            return String.format("valContract=%s, withdrawalAddress=%s, weiAmount=%s, ethAmount=%s",
                    valContract, withdrawalAddress, weiAmount, ethAmount);
        } catch (Exception ex) {
            return PARSE_ERRROR;
        }
    }

    private String parseLogoutData(byte[] logoutData) {
        try {
            RLPList data = (RLPList) RLP.decode2(logoutData).get(0);
            long validatorIndex = ByteUtil.byteArrayToLong(data.get(0).getRLPData());
            long targetEpoch = ByteUtil.byteArrayToLong(data.get(1).getRLPData());

            return String.format("validatorIndex=%s, targetEpoch=%s",
                    validatorIndex, targetEpoch);
        } catch (Exception ex) {
            return PARSE_ERRROR;
        }
    }

    private String parseWithdrawData(BigInteger validatorIndex, TransactionReceipt receipt) {
        try {
            return String.format("validatorIndex=%s, withdrawalAddress=0x%s, weiAmount=%s",
                    validatorIndex, "ImplementMe", "ImplementMe"); // TODO: Implement when logs are available
        } catch (Exception ex) {
            return PARSE_ERRROR;
        }
    }

    private String parseVoteData(byte[] voteData) {
        try {
            RLPList data = (RLPList) RLP.decode2(voteData).get(0);
            long validatorIndex = ByteUtil.byteArrayToLong(data.get(0).getRLPData());
            String targetHash = Hex.toHexString(data.get(1).getRLPData());
            long targetEpoch = ByteUtil.byteArrayToLong(data.get(2).getRLPData());
            long sourceEpoch = ByteUtil.byteArrayToLong(data.get(3).getRLPData());

            return String.format("validatorIndex=%s, targetHash=0x%s, targetEpoch=%s, sourceEpoch=%s",
                    validatorIndex, targetHash, targetEpoch, sourceEpoch);
        } catch (Exception ex) {
            return PARSE_ERRROR;
        }
    }

    public Queue<String> getCasperLogs() {
        return lastCasperLogs;
    }

    private void addCasperMessage(String message) {
        lastCasperLogs.add(message);
        if (lastCasperLogs.size() > KEEP_LOG_ENTRIES) {
            lastCasperLogs.poll();
        }
        clientMessageService.sendToTopic("/topic/casperLog", message);
    }
}
