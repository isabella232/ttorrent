/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.turn.ttorrent.client;

import com.turn.ttorrent.tracker.simple.SimpleTracker;
import com.turn.ttorrent.client.peer.PeerExistenceListener;
import com.turn.ttorrent.tracker.client.TorrentMetadataProvider;
import com.turn.ttorrent.protocol.torrent.Torrent;
import com.turn.ttorrent.protocol.torrent.TorrentCreator;
import com.turn.ttorrent.test.TestPeerExistenceListener;
import com.turn.ttorrent.tracker.client.test.TestTorrentMetadataProvider;
import com.turn.ttorrent.protocol.test.TorrentTestUtils;
import com.turn.ttorrent.protocol.tracker.TrackerMessage;
import com.turn.ttorrent.tracker.TrackedTorrent;
import java.io.File;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.junit.Assert.*;

/**
 *
 * @author shevek
 */
public class TrackerHandlerTest {

    private static final Logger LOG = LoggerFactory.getLogger(TrackerHandlerTest.class);

    @Test
    public void testTracking() throws Exception {
        SimpleTracker tracker = new SimpleTracker(new InetSocketAddress("localhost", 5674));
        tracker.start();

        try {
            File dir = TorrentTestUtils.newTorrentDir("c_seed");
            TorrentCreator creator = TorrentTestUtils.newTorrentCreator(dir, 12345678);
            List<URI> tier1 = new ArrayList<URI>(tracker.getAnnounceUris());
            List<URI> tier0 = Arrays.asList(URI.create("http://localhost:100/"), URI.create("http://1.1.1.1:101/"));
            creator.setAnnounceTiers(Arrays.asList(tier0, tier1));
            Torrent torrent = creator.create();

            TrackedTorrent trackedTorrent = tracker.addTorrent(torrent);
            trackedTorrent.setAnnounceInterval(1, TimeUnit.MILLISECONDS);

            Client client = new Client(getClass().getSimpleName());
            client.start();

            try {
                final CountDownLatch latch = new CountDownLatch(2);
                TorrentMetadataProvider torrentMetadataProvider = new TestTorrentMetadataProvider(torrent.getInfoHash(), torrent.getAnnounceList());
                PeerExistenceListener existenceListener = new TestPeerExistenceListener() {
                    @Override
                    public void addPeers(Map<? extends SocketAddress, ? extends byte[]> peers, String reason) {
                        super.addPeers(peers, reason);
                        latch.countDown();
                    }
                };
                final AtomicInteger moveCount = new AtomicInteger(0);
                TrackerHandler trackerHandler = new TrackerHandler(client, torrentMetadataProvider, existenceListener) {
                    @Override
                    boolean moveToNextTracker(TrackerHandler.TrackerState curr, String reason) {
                        moveCount.getAndIncrement();
                        return super.moveToNextTracker(curr, reason);
                    }
                };
                LOG.info("TrackerHandler is " + trackerHandler);
                trackerHandler.start();

                latch.await(30, TimeUnit.SECONDS);
                assertEquals(0, latch.getCount());
                assertEquals(2, moveCount.get());

                trackerHandler.stop();
            } finally {
                client.stop();
            }
        } finally {
            tracker.stop();
        }
    }

    @Test
    public void testFailover() throws Exception {
        Client client = new Client(getClass().getSimpleName());
        client.start();

        try {
            byte[] infoHash = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
            URI uri0 = new URI("http://localhost:100/announce");  // Deliberately invalid.
            URI uri1 = new URI("http://localhost:101/announce");  // Deliberately invalid.
            List<URI> uris = Arrays.asList(uri0, uri1);
            TestTorrentMetadataProvider metadataProvider = new TestTorrentMetadataProvider(infoHash, Arrays.asList(uris));
            PeerExistenceListener existenceListener = new TestPeerExistenceListener();
            TrackerHandler trackerHandler = new TrackerHandler(client, metadataProvider, existenceListener) {
                @Override
                /* pp */ TrackerHandler.TrackerState run_once(TrackerMessage.AnnounceEvent event) {
                    LOG.info("Running TrackerHandler: " + event);
                    return null;
                }

                @Override
                public void run() {
                    LOG.info("Running TrackerHandler.");
                }
            };
            LOG.info("TrackerHandler is " + trackerHandler);
            trackerHandler.start();

            Thread.sleep(1000);

            try {

                assertEquals("uri0: Initial", uri0, trackerHandler.getCurrentTracker().getUri());
                trackerHandler.handleAnnounceFailed(uri1, TrackerMessage.AnnounceEvent.NONE, "no-change fail");
                assertEquals("uri0: Announce-fail on not-used uri1", uri0, trackerHandler.getCurrentTracker().getUri());

                trackerHandler.handleAnnounceFailed(uri0, TrackerMessage.AnnounceEvent.NONE, "change fail");
                assertEquals("uri1: Announced fail on current uri0", uri1, trackerHandler.getCurrentTracker().getUri());
                trackerHandler.handleAnnounceFailed(uri0, TrackerMessage.AnnounceEvent.NONE, "no-change fail");
                assertEquals("uri1: Announced second fail on non-current uri0", uri1, trackerHandler.getCurrentTracker().getUri());

                trackerHandler.handleAnnounceFailed(uri1, TrackerMessage.AnnounceEvent.NONE, "change fail");
                assertEquals("uri0: Now we fail uri1", uri0, trackerHandler.getCurrentTracker().getUri());
                trackerHandler.handleAnnounceFailed(uri1, TrackerMessage.AnnounceEvent.NONE, "no-change fail");
                assertEquals("uri0: Now we fail uri1 again, but we aren't using it.", uri0, trackerHandler.getCurrentTracker().getUri());

                LOG.info("Done.");
            } finally {
                trackerHandler.stop();
            }

        } finally {
            client.stop();
        }

    }

    @Ignore("Not yet implemented.")
    @Test
    public void testMultipleTrackers() {
    }
}
