/**
 * Copyright (C) 2012 Turn, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.turn.ttorrent.common.protocol.http;

import com.google.common.net.InetAddresses;
import com.turn.ttorrent.common.Peer;
import com.turn.ttorrent.common.Torrent;
import com.turn.ttorrent.common.protocol.TrackerMessage.AnnounceRequestMessage;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.Map;
import javax.annotation.Nonnull;

/**
 * The announce request message for the HTTP tracker protocol.
 *
 * <p>
 * This class represents the announce request message in the HTTP tracker
 * protocol. It doesn't add any specific fields compared to the generic
 * announce request message, but it provides the means to parse such
 * messages and craft them.
 * </p>
 *
 * @author mpetazzoni
 */
public class HTTPAnnounceRequestMessage extends HTTPTrackerMessage
        implements AnnounceRequestMessage {

    private final byte[] infoHash;
    private final Peer peer;
    private final long uploaded;
    private final long downloaded;
    private final long left;
    private final boolean compact;
    private final boolean noPeerId;
    private final RequestEvent event;
    private final int numWant;

    public HTTPAnnounceRequestMessage(
            byte[] infoHash, Peer peer,
            long uploaded, long downloaded, long left,
            boolean compact, boolean noPeerId, RequestEvent event, int numWant) {
        super(Type.ANNOUNCE_REQUEST);
        this.infoHash = infoHash;
        this.peer = peer;
        this.downloaded = downloaded;
        this.uploaded = uploaded;
        this.left = left;
        this.compact = compact;
        this.noPeerId = noPeerId;
        this.event = event;
        this.numWant = numWant;
    }

    @Override
    public byte[] getInfoHash() {
        return this.infoHash;
    }

    @Override
    public String getHexInfoHash() {
        return Torrent.byteArrayToHexString(this.infoHash);
    }

    @Override
    public Peer getPeer() {
        return peer;
    }

    @Override
    public long getUploaded() {
        return this.uploaded;
    }

    @Override
    public long getDownloaded() {
        return this.downloaded;
    }

    @Override
    public long getLeft() {
        return this.left;
    }

    @Override
    public boolean getCompact() {
        return this.compact;
    }

    @Override
    public boolean getNoPeerIds() {
        return this.noPeerId;
    }

    @Override
    public RequestEvent getEvent() {
        return this.event;
    }

    @Override
    public int getNumWant() {
        return this.numWant;
    }

    @Nonnull
    private String toUrlString(@Nonnull byte[] data) throws UnsupportedEncodingException {
        String text = new String(data, Torrent.BYTE_ENCODING);
        return URLEncoder.encode(text, Torrent.BYTE_ENCODING_NAME);
    }

    /**
     * Build the announce request URL for the given tracker announce URL.
     *
     * @param trackerAnnounceURL The tracker's announce URL.
     * @return The URL object representing the announce request URL.
     */
    public URI toURI(URI trackerAnnounceURL)
            throws UnsupportedEncodingException, URISyntaxException {
        String base = trackerAnnounceURL.toString();
        StringBuilder url = new StringBuilder(base);
        url.append(base.contains("?") ? "&" : "?")
                .append("info_hash=").append(toUrlString(getInfoHash()))
                .append("&peer_id=").append(toUrlString(getPeer().getPeerId()))
                .append("&port=").append(getPeer().getPort())
                .append("&uploaded=").append(getUploaded())
                .append("&downloaded=").append(getDownloaded())
                .append("&left=").append(getLeft())
                .append("&compact=").append(getCompact() ? 1 : 0)
                .append("&no_peer_id=").append(getNoPeerIds() ? 1 : 0);

        if (getEvent() != null
                && !RequestEvent.NONE.equals(getEvent())) {
            url.append("&event=").append(getEvent().getEventName());
        }

        String ip = getPeer().getIp();
        if (ip != null)
            url.append("&ip=").append(ip);

        if (getNumWant() != AnnounceRequestMessage.DEFAULT_NUM_WANT)
            url.append("&numwant=").append(getNumWant());

        return new URI(url.toString());
    }

    public static HTTPAnnounceRequestMessage fromParams(Map<String, String> params)
            throws IOException, MessageValidationException {

        byte[] infoHash = toBytes(params, "info_hash", ErrorMessage.FailureReason.MISSING_HASH);
        byte[] peerId = toBytes(params, "peer_id", ErrorMessage.FailureReason.MISSING_PEER_ID);
        int port = toInt(params, "port", -1, ErrorMessage.FailureReason.MISSING_PORT);

        // Default 'uploaded' and 'downloaded' to 0 if the client does
        // not provide it (although it should, according to the spec).
        long uploaded = toLong(params, "uploaded", 0, null);
        long downloaded = toLong(params, "downloaded", 0, null);
        // Default 'left' to -1 to avoid peers entering the COMPLETED
        // state when they don't provide the 'left' parameter.
        long left = toLong(params, "left", -1, null);

        boolean compact = toBoolean(params, "compact");
        boolean noPeerId = toBoolean(params, "no_peer_id");

        int numWant = toInt(params, "numwant", AnnounceRequestMessage.DEFAULT_NUM_WANT, null);
        String ip = toString(params, "ip", null);

        RequestEvent event = RequestEvent.NONE;
        if (params.containsKey("event")) {
            event = RequestEvent.getByName(params.get("event"));
        }

        InetSocketAddress address = new InetSocketAddress(ip, port);
        return new HTTPAnnounceRequestMessage(infoHash,
                new Peer(address, peerId),
                uploaded, downloaded, left, compact, noPeerId,
                event, numWant);
    }

    public static HTTPAnnounceRequestMessage craft(byte[] infoHash,
            byte[] peerId, int port, long uploaded, long downloaded, long left,
            boolean compact, boolean noPeerId, RequestEvent event,
            String ip, int numWant)
            throws IOException, MessageValidationException, UnsupportedEncodingException {
        InetSocketAddress address = new InetSocketAddress(ip, port);
        return new HTTPAnnounceRequestMessage(
                infoHash, new Peer(address, peerId),
                uploaded, downloaded, left, compact, noPeerId, event, numWant);
    }
}
