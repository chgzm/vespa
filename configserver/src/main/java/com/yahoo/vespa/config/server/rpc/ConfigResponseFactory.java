// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.rpc;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.text.AbstractUtf8Array;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.PayloadChecksum;
import com.yahoo.vespa.config.PayloadChecksums;
import com.yahoo.vespa.config.protocol.ConfigResponse;
import com.yahoo.vespa.config.util.ConfigUtils;

import static com.yahoo.vespa.config.PayloadChecksum.Type.MD5;
import static com.yahoo.vespa.config.PayloadChecksum.Type.XXHASH64;

/**
 * Represents a component that creates config responses from a payload. Different implementations
 * can do transformations of the payload such as compression.
 *
 * @author Ulf Lilleengen
 */
public interface ConfigResponseFactory {

    static ConfigResponseFactory create(ConfigserverConfig configserverConfig) {
        switch (configserverConfig.payloadCompressionType()) {
            case LZ4:
                return new LZ4ConfigResponseFactory();
            case UNCOMPRESSED:
                return new UncompressedConfigResponseFactory();
            default:
                throw new IllegalArgumentException("Unknown payload compression type " + configserverConfig.payloadCompressionType());
        }
    }

    /**
     * Creates a {@link ConfigResponse} for a given payload and generation.
     *
     * @param rawPayload                the {@link ConfigPayload} to put in the response
     * @param generation                the payload generation
     * @param applyOnRestart            true if this config change should only be applied on restart,
     *                                  false if it should be applied immediately
     * @param requestsPayloadChecksums  payload checksums from requests
     * @return a {@link ConfigResponse} that can be sent to the client
     */
    ConfigResponse createResponse(AbstractUtf8Array rawPayload,
                                  long generation,
                                  boolean applyOnRestart,
                                  PayloadChecksums requestsPayloadChecksums);

    /** Generates payload checksums based on what type of checksums exist in request */
    default PayloadChecksums generatePayloadChecksums(AbstractUtf8Array rawPayload, PayloadChecksums requestsPayloadChecksums) {
        PayloadChecksum requestChecksumMd5 = requestsPayloadChecksums.getForType(MD5);
        PayloadChecksum requestChecksumXxhash64 = requestsPayloadChecksums.getForType(XXHASH64);

        PayloadChecksum md5 = PayloadChecksum.empty(MD5);
        PayloadChecksum xxhash64 = PayloadChecksum.empty(XXHASH64);
        // Response contains same checksum type as in request, except when both are empty,
        // then use both checksum types in response
        if (requestChecksumMd5.isEmpty() && requestChecksumXxhash64.isEmpty()
                || ( ! requestChecksumMd5.isEmpty() && ! requestChecksumXxhash64.isEmpty())) {
            md5 = new PayloadChecksum(ConfigUtils.getMd5(rawPayload), MD5);
            xxhash64 = new PayloadChecksum(ConfigUtils.getXxhash64(rawPayload), XXHASH64);
        } else if ( ! requestChecksumMd5.isEmpty())
            md5 = new PayloadChecksum(ConfigUtils.getMd5(rawPayload), MD5);
        else
            xxhash64 = new PayloadChecksum(ConfigUtils.getXxhash64(rawPayload), XXHASH64);

        return PayloadChecksums.from(md5, xxhash64);
    }

}
