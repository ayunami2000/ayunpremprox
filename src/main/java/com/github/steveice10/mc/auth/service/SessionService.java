package com.github.steveice10.mc.auth.service;

import com.github.steveice10.mc.auth.data.GameProfile;
import com.github.steveice10.mc.auth.data.GameProfile.Property;
import com.github.steveice10.mc.auth.exception.profile.ProfileException;
import com.github.steveice10.mc.auth.exception.profile.ProfileLookupException;
import com.github.steveice10.mc.auth.exception.profile.ProfileNotFoundException;
import com.github.steveice10.mc.auth.exception.request.RequestException;
import com.github.steveice10.mc.auth.util.HTTP;
import com.github.steveice10.mc.auth.util.UUIDSerializer;
import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;

public class SessionService extends Service {
    private static final URI DEFAULT_BASE_URI = URI.create("https://sessionserver.mojang.com/session/minecraft/");
    private static final String JOIN_ENDPOINT = "join";
    private static final String HAS_JOINED_ENDPOINT = "hasJoined";
    private static final String PROFILE_ENDPOINT = "profile";

    public SessionService() {
        super(DEFAULT_BASE_URI);
    }

    public SessionService(URI uri) {
        super(uri);
    }

    public String getServerId(String base, PublicKey publicKey, SecretKey secretKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            digest.update(base.getBytes(StandardCharsets.ISO_8859_1));
            digest.update(secretKey.getEncoded());
            digest.update(publicKey.getEncoded());
            return (new BigInteger(digest.digest())).toString(16);
        } catch (NoSuchAlgorithmException var5) {
            throw new IllegalStateException("Server ID hash algorithm unavailable.", var5);
        }
    }

    public void joinServer(GameProfile profile, String authenticationToken, String serverId) throws RequestException {
        SessionService.JoinServerRequest request = new SessionService.JoinServerRequest(authenticationToken, profile.getId(), serverId);
        HTTP.makeRequest(this.getProxy(), this.getEndpointUri("join"), request, (Class)null);
    }

    public GameProfile getProfileByServer(String name, String serverId) throws RequestException {
        Map<String, String> queryParams = new HashMap();
        queryParams.put("username", name);
        queryParams.put("serverId", serverId);
        SessionService.HasJoinedResponse response = (SessionService.HasJoinedResponse)HTTP.makeRequest(this.getProxy(), this.getEndpointUri("hasJoined", queryParams), (Object)null, SessionService.HasJoinedResponse.class);
        if (response != null && response.id != null) {
            GameProfile result = new GameProfile(response.id, name);
            result.setProperties(response.properties);
            return result;
        } else {
            return null;
        }
    }

    public GameProfile fillProfileProperties(GameProfile profile) throws ProfileException {
        if (profile.getId() == null) {
            return profile;
        } else {
            try {
                SessionService.MinecraftProfileResponse response = (SessionService.MinecraftProfileResponse)HTTP.makeRequest(this.getProxy(), this.getEndpointUri("profile/" + UUIDSerializer.fromUUID(profile.getId()), Collections.singletonMap("unsigned", "false")), (Object)null, SessionService.MinecraftProfileResponse.class);
                if (response == null) {
                    throw new ProfileNotFoundException("Couldn't fetch profile properties for " + profile + " as the profile does not exist.");
                } else {
                    profile.setProperties(response.properties);
                    return profile;
                }
            } catch (RequestException var3) {
                throw new ProfileLookupException("Couldn't look up profile properties for " + profile + ".", var3);
            }
        }
    }

    public String toString() {
        return "SessionService{}";
    }

    private static class MinecraftProfileResponse {
        public UUID id;
        public String name;
        public List<Property> properties;

        private MinecraftProfileResponse() {
        }
    }

    private static class HasJoinedResponse {
        public UUID id;
        public List<Property> properties;

        private HasJoinedResponse() {
        }
    }

    private static class JoinServerRequest {
        private String accessToken;
        private UUID selectedProfile;
        private String serverId;

        protected JoinServerRequest(String accessToken, UUID selectedProfile, String serverId) {
            this.accessToken = accessToken;
            this.selectedProfile = selectedProfile;
            this.serverId = serverId;
        }
    }
}