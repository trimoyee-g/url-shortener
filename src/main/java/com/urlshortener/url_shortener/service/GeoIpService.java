package com.urlshortener.url_shortener.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;

import java.io.IOException;
import java.net.InetAddress;

@Slf4j
@Service
public class GeoIpService {

    private final DatabaseReader reader;

    public GeoIpService(DatabaseReader reader) {
        this.reader = reader;
    }

    /**
     * Returns the ISO 3166-1 alpha-2 country code for the given IP address.
     * Returns "XX" if the IP is private, loopback, or lookup fails.
     */
    public String getCountryCode(String ip) {
        try {
            InetAddress address = InetAddress.getByName(ip);

            // Private/loopback IPs have no country — return unknown
            if (address.isLoopbackAddress() || address.isSiteLocalAddress()) {
                return "XX";
            }

            return reader.country(address).getCountry().getIsoCode();

        } catch (IOException | GeoIp2Exception e) {
            log.debug("GeoIP lookup failed for {}: {}", ip, e.getMessage());
            return "XX";
        }
    }
}