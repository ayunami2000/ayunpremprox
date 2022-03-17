package me.ayunami2000.ayunpremprox;

import com.github.steveice10.mc.auth.data.GameProfile;
import com.github.steveice10.mc.auth.data.GameProfile.Property;
import com.github.steveice10.mc.auth.exception.request.InvalidCredentialsException;
import com.github.steveice10.mc.auth.exception.request.RequestException;
import com.github.steveice10.mc.auth.service.AuthenticationService;
import com.github.steveice10.mc.auth.util.HTTP;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class AlteningAuthService extends AuthenticationService {
    private static final URI DEFAULT_BASE_URI = URI.create("http://authserver.thealtening.com/");
    private static final String AUTHENTICATE_ENDPOINT = "authenticate";
    private static final String REFRESH_ENDPOINT = "refresh";
    private static final String INVALIDATE_ENDPOINT = "invalidate";
    private String id;
    private String clientToken;

    public AlteningAuthService() {
        this(UUID.randomUUID().toString());
    }

    public AlteningAuthService(String clientToken) {
        super(DEFAULT_BASE_URI);
        if (clientToken == null) {
            throw new IllegalArgumentException("ClientToken cannot be null.");
        } else {
            this.clientToken = clientToken;
        }
    }

    public String getId() {
        return this.id;
    }

    public String getClientToken() {
        return this.clientToken;
    }

    public void login() throws RequestException {
        if (this.username != null && !this.username.equals("")) {
            boolean token = this.accessToken != null && !this.accessToken.equals("");
            boolean password = this.password != null && !this.password.equals("");
            if (!token && !password) {
                throw new InvalidCredentialsException("Invalid password or access token.");
            } else {
                AlteningAuthService.AuthenticateRefreshResponse response;
                if (token) {
                    AlteningAuthService.RefreshRequest request = new AlteningAuthService.RefreshRequest(this.clientToken, this.accessToken, (GameProfile)null);
                    response = (AlteningAuthService.AuthenticateRefreshResponse)HTTP.makeRequest(this.getProxy(), this.getEndpointUri("refresh"), request, AlteningAuthService.AuthenticateRefreshResponse.class);
                } else {
                    AlteningAuthService.AuthenticationRequest request = new AlteningAuthService.AuthenticationRequest(this.username, this.password, this.clientToken);
                    response = (AlteningAuthService.AuthenticateRefreshResponse)HTTP.makeRequest(this.getProxy(), this.getEndpointUri("authenticate"), request, AlteningAuthService.AuthenticateRefreshResponse.class);
                }

                if (response == null) {
                    throw new RequestException("Server returned invalid response.");
                } else if (!response.clientToken.equals(this.clientToken)) {
                    throw new RequestException("Server responded with incorrect client token.");
                } else {
                    if (response.user != null && response.user.id != null) {
                        this.id = response.user.id;
                    } else {
                        this.id = this.username;
                    }

                    this.accessToken = response.accessToken;
                    this.profiles = response.availableProfiles != null ? Arrays.asList(response.availableProfiles) : Collections.emptyList();
                    this.selectedProfile = response.selectedProfile;
                    this.properties.clear();
                    if (response.user != null && response.user.properties != null) {
                        this.properties.addAll(response.user.properties);
                    }

                    this.loggedIn = true;
                }
            }
        } else {
            throw new InvalidCredentialsException("Invalid username.");
        }
    }

    public void logout() throws RequestException {
        AlteningAuthService.InvalidateRequest request = new AlteningAuthService.InvalidateRequest(this.clientToken, this.accessToken);
        HTTP.makeRequest(this.getProxy(), this.getEndpointUri("invalidate"), request);
        super.logout();
        this.id = null;
    }

    public void selectGameProfile(GameProfile profile) throws RequestException {
        if (!this.loggedIn) {
            throw new RequestException("Cannot change game profile while not logged in.");
        } else if (this.selectedProfile != null) {
            throw new RequestException("Cannot change game profile when it is already selected.");
        } else if (profile != null && this.profiles.contains(profile)) {
            AlteningAuthService.RefreshRequest request = new AlteningAuthService.RefreshRequest(this.clientToken, this.accessToken, profile);
            AlteningAuthService.AuthenticateRefreshResponse response = (AlteningAuthService.AuthenticateRefreshResponse)HTTP.makeRequest(this.getProxy(), this.getEndpointUri("refresh"), request, AlteningAuthService.AuthenticateRefreshResponse.class);
            if (response == null) {
                throw new RequestException("Server returned invalid response.");
            } else if (!response.clientToken.equals(this.clientToken)) {
                throw new RequestException("Server responded with incorrect client token.");
            } else {
                this.accessToken = response.accessToken;
                this.selectedProfile = response.selectedProfile;
            }
        } else {
            throw new IllegalArgumentException("Invalid profile '" + profile + "'.");
        }
    }

    private static class AuthenticateRefreshResponse {
        public String accessToken;
        public String clientToken;
        public GameProfile selectedProfile;
        public GameProfile[] availableProfiles;
        public AlteningAuthService.User user;

        private AuthenticateRefreshResponse() {
        }
    }

    private static class InvalidateRequest {
        private String clientToken;
        private String accessToken;

        protected InvalidateRequest(String clientToken, String accessToken) {
            this.clientToken = clientToken;
            this.accessToken = accessToken;
        }
    }

    private static class RefreshRequest {
        private String clientToken;
        private String accessToken;
        private GameProfile selectedProfile;
        private boolean requestUser;

        protected RefreshRequest(String clientToken, String accessToken, GameProfile selectedProfile) {
            this.clientToken = clientToken;
            this.accessToken = accessToken;
            this.selectedProfile = selectedProfile;
            this.requestUser = true;
        }
    }

    private static class AuthenticationRequest {
        private AlteningAuthService.Agent agent = new AlteningAuthService.Agent("Minecraft", 1);
        private String username;
        private String password;
        private String clientToken;
        private boolean requestUser;

        protected AuthenticationRequest(String username, String password, String clientToken) {
            this.username = username;
            this.password = password;
            this.clientToken = clientToken;
            this.requestUser = true;
        }
    }

    private static class User {
        public String id;
        public List<Property> properties;

        private User() {
        }
    }

    private static class Agent {
        private String name;
        private int version;

        protected Agent(String name, int version) {
            this.name = name;
            this.version = version;
        }
    }
}