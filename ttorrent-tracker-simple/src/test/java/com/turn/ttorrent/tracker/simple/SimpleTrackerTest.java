/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.turn.ttorrent.tracker.simple;

import com.turn.ttorrent.tracker.TrackerUtils;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import javax.annotation.Nonnull;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author shevek
 */
public class SimpleTrackerTest {

    private static final Logger LOG = LoggerFactory.getLogger(SimpleTrackerTest.class);
    private static final String[] PATHS = {
        "/",
        "/foo",
        "/announce",
        "/announce?foo"
    };

    private void test(@Nonnull SimpleTracker tracker) throws Exception {
        LOG.info("Before start: " + tracker.getAnnounceUris());
        tracker.start();
        try {
            LOG.info("Running: " + tracker.getAnnounceUris());
            CloseableHttpClient client = HttpClientBuilder.create().build();
            for (URI uri : tracker.getAnnounceUris()) {
                for (String path : PATHS) {
                    HttpGet request = new HttpGet(uri.resolve(path));
                    CloseableHttpResponse response = client.execute(request);
                    LOG.info(request + " -> " + response);
                    response.close();
                }
            }
        } finally {
            tracker.stop();
        }
        LOG.info("Done.");
    }

    private void testTracker(@Nonnull InetSocketAddress address) throws Exception {
        SimpleTracker tracker = new SimpleTracker();
        tracker.addListenAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        test(tracker);
    }

    @Test
    public void testLoopback() throws Exception {
        testTracker(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
    }

    @Test
    public void testPort() throws Exception {
        testTracker(new InetSocketAddress(TrackerUtils.DEFAULT_TRACKER_PORT));
    }

    @Test
    public void testInaddrLoopback() throws Exception {
        testTracker(new InetSocketAddress(InetAddress.getLoopbackAddress(), TrackerUtils.DEFAULT_TRACKER_PORT));
    }

    @Test
    public void testInaddrAny() throws Exception {
        testTracker(new InetSocketAddress("0.0.0.0", TrackerUtils.DEFAULT_TRACKER_PORT));
    }
}