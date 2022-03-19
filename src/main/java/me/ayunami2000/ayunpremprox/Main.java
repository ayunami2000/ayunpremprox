package me.ayunami2000.ayunpremprox;

import com.github.steveice10.mc.auth.data.GameProfile;
import com.github.steveice10.mc.auth.exception.request.RequestException;
import com.github.steveice10.mc.auth.service.*;
import com.github.steveice10.mc.protocol.MinecraftConstants;
import com.github.steveice10.mc.protocol.MinecraftProtocol;
import com.github.steveice10.mc.protocol.ServerLoginHandler;
import com.github.steveice10.mc.protocol.codec.MinecraftCodec;
import com.github.steveice10.mc.protocol.data.status.PlayerInfo;
import com.github.steveice10.mc.protocol.data.status.ServerStatusInfo;
import com.github.steveice10.mc.protocol.data.status.VersionInfo;
import com.github.steveice10.mc.protocol.data.status.handler.ServerInfoBuilder;
import com.github.steveice10.mc.protocol.packet.handshake.serverbound.ClientIntentionPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundChatPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundDisconnectPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.ServerboundKeepAlivePacket;
import com.github.steveice10.mc.protocol.packet.login.clientbound.ClientboundGameProfilePacket;
import com.github.steveice10.mc.protocol.packet.login.clientbound.ClientboundHelloPacket;
import com.github.steveice10.mc.protocol.packet.login.clientbound.ClientboundLoginCompressionPacket;
import com.github.steveice10.mc.protocol.packet.login.serverbound.ServerboundHelloPacket;
import com.github.steveice10.packetlib.Server;
import com.github.steveice10.packetlib.Session;
import com.github.steveice10.packetlib.event.server.ServerAdapter;
import com.github.steveice10.packetlib.event.server.SessionAddedEvent;
import com.github.steveice10.packetlib.event.server.SessionRemovedEvent;
import com.github.steveice10.packetlib.event.session.DisconnectedEvent;
import com.github.steveice10.packetlib.event.session.SessionAdapter;
import com.github.steveice10.packetlib.packet.Packet;
import com.github.steveice10.packetlib.tcp.TcpClientSession;
import com.github.steveice10.packetlib.tcp.TcpServer;

import java.io.IOException;
import java.net.Proxy;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import com.thealtening.api.TheAltening;
import com.thealtening.api.response.Account;
import com.thealtening.api.retriever.BasicDataRetriever;
import net.kyori.adventure.text.Component;

import javax.net.ssl.HttpsURLConnection;

public class Main {
    private static Map<Session,Session> srvToCli = new HashMap<>();
    private static List<String[]> accs = new ArrayList<>();
    private static List<Integer> takenAccs = new ArrayList<>();
    private static boolean isCracked=false;
    private static boolean forwardUsername=false;
    private static boolean useAltening=false;
    private static BasicDataRetriever alteningApi = null;
    private static URI alteningUri = URI.create("http://sessionserver.thealtening.com/session/minecraft/");
    private static String[] servers = {"mh-prd.minehut.com"};

    public static void main(String[] args){
        HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);

        if(args.length>0){
            if(args[0].equalsIgnoreCase("cracked"))isCracked=true;
            if(args[0].equalsIgnoreCase("forward"))isCracked=forwardUsername=true;
            if(args[0].equalsIgnoreCase("thealtening"))useAltening=true;
        }

        if(args.length>2)servers=args[2].split(",");

        if(!isCracked&&!useAltening) {
            try {
                String[] accList = new String(Files.readAllBytes(Path.of("alts.txt"))).trim().replaceAll("\\r", "").split("\n");
                for (String acc : accList) {
                    accs.add(acc.split(":", 2));
                }
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }
        if(useAltening){
            try {
                String key = new String(Files.readAllBytes(Path.of("altkey.txt"))).trim();
                alteningApi = TheAltening.newBasicRetriever(key);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }

        SessionService sessionService = new SessionService();
        sessionService.setProxy(Proxy.NO_PROXY);

        Server server = new TcpServer("127.0.0.1", args.length<2?25569:Integer.parseInt(args[1]), MinecraftProtocol::new);
        server.setGlobalFlag(MinecraftConstants.SESSION_SERVICE_KEY, sessionService);
        server.setGlobalFlag(MinecraftConstants.VERIFY_USERS_KEY, false);
        server.setGlobalFlag(MinecraftConstants.SERVER_INFO_BUILDER_KEY, (ServerInfoBuilder) session ->
                new ServerStatusInfo(
                        new VersionInfo(MinecraftCodec.CODEC.getMinecraftVersion(), MinecraftCodec.CODEC.getProtocolVersion()),
                        new PlayerInfo(42069, 0, new GameProfile[0]),
                        Component.text("ayunpremprox"),
                        null
                )
        );
        server.setGlobalFlag(MinecraftConstants.SERVER_COMPRESSION_THRESHOLD, 256);
        server.addListener(new ServerAdapter() {
            @Override
            public void sessionAdded(SessionAddedEvent event) {
                final String[] un = {""};
                final Session[] cliSession = new Session[1];
                event.getSession().setFlag(MinecraftConstants.SERVER_LOGIN_HANDLER_KEY, (ServerLoginHandler) session -> {
                    cliSession[0] = login(session, args, un[0]);
                    if (cliSession[0] == null) {
                        session.disconnect("");
                        return;
                    }
                    srvToCli.put(session, cliSession[0]);
                });
                event.getSession().addListener(new SessionAdapter() {
                    @Override
                    public void packetReceived(Session session, Packet packet) {
                        if(forwardUsername&&packet instanceof ServerboundHelloPacket){
                            un[0] = ((ServerboundHelloPacket)packet).getUsername();
                        }
                        if(packet instanceof ClientIntentionPacket){
                            if(((ClientIntentionPacket)packet).getProtocolVersion()<MinecraftCodec.CODEC.getProtocolVersion()){
                                String discMsg = "\"Sorry, but this server only supports version "+MinecraftCodec.CODEC.getMinecraftVersion()+"\nConsider using a mod like ViaFabric to make your life easier!\"";
                                session.send(new ClientboundDisconnectPacket(discMsg));
                                session.disconnect("");
                                return;
                            }
                        }
                        if(cliSession[0]==null)return;
                        if(packet instanceof ClientIntentionPacket)return;
                        if(packet instanceof ServerboundHelloPacket)return;
                        if(packet instanceof ServerboundKeepAlivePacket)return;
                        //System.out.println("srv "+packet);
                        cliSession[0].send(packet);
                    }
                });
            }

            @Override
            public void sessionRemoved(SessionRemovedEvent event) {
                //kick client...
                //System.out.println("srv disc");
                Session cliSession = srvToCli.get(event.getSession());
                if(cliSession!=null) {
                    cliSession.disconnect("");
                    srvToCli.remove(event.getSession(), cliSession);
                }
            }
        });

        server.bind();
    }

    private static String getSaltString(int len) {
        String SALTCHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz1234567890";
        StringBuilder salt = new StringBuilder();
        Random rnd = new Random();
        while (salt.length() < len) {
            int index = (int) (rnd.nextFloat() * SALTCHARS.length());
            salt.append(SALTCHARS.charAt(index));
        }
        return salt.toString();
    }

    private static Session login(Session srvSession, String[] args, String crackedUsername){
        int accIndex = 0;
        String[] userpass = new String[]{"",""};

        if(isCracked) {
            userpass[0] = forwardUsername ? crackedUsername : getSaltString(10);
        }else if(useAltening){
            //do nothing
        }else {
            while (takenAccs.contains(accIndex)) accIndex++;
            if (accs.size() <= accIndex) {
                return null;//out of accounts :(
            }
            takenAccs.add(accIndex);
            userpass = accs.get(accIndex);
        }

        MinecraftProtocol protocol;
        AuthenticationService authService = null;
        try {
            if(isCracked) {
                protocol = new MinecraftProtocol(userpass[0]);
            }else {
                if(useAltening){
                    Account acc = alteningApi.getAccount();
                    authService = new AlteningAuthService();
                    userpass[0] = acc.getToken();
                    userpass[1] = "ayunpremprox";
                }else if(userpass[0].startsWith("<MS> ")){
                    authService = new MsaAuthenticationService("ayunpremprox"+Math.random());
                    userpass[0] = userpass[0].substring(5);
                }else {
                    authService = new MojangAuthenticationService();
                }
                authService.setUsername(userpass[0]);
                authService.setPassword(userpass[1]);
                authService.setProxy(Proxy.NO_PROXY);
                authService.login();

                protocol = new MinecraftProtocol(authService.getSelectedProfile(), authService.getAccessToken());
            }
            System.out.println("Logged in user "+userpass[0]);
        } catch (RequestException e) {
            e.printStackTrace();
            return null;
        }

        SessionService sessionService;
        if(useAltening){
            sessionService = new SessionService(alteningUri);
        }else {
            sessionService = new SessionService();
        }
        sessionService.setProxy(Proxy.NO_PROXY);

        String[] srv = servers[(int)Math.floor(Math.random()*servers.length)].split(":");
        int p=25565;
        try{
            p=Integer.parseInt(srv[1]);
        }catch(NumberFormatException | ArrayIndexOutOfBoundsException e){}

        Session client = new TcpClientSession(srv[0], p, protocol, null);
        client.setFlag(MinecraftConstants.SESSION_SERVICE_KEY, sessionService);
        int finalAccIndex = accIndex;
        String[] finalUserpass = userpass;
        AuthenticationService finalAuthService = authService;
        client.addListener(new SessionAdapter() {
            @Override
            public void packetReceived(Session session, Packet packet) {
                //packet get from server! send to real client...
                if(packet instanceof ClientboundHelloPacket)return;
                if(packet instanceof ClientboundLoginCompressionPacket)return;
                if(packet instanceof ClientboundGameProfilePacket)return;
                //System.out.println("cli "+packet);
                srvSession.send(packet);
            }

            @Override
            public void disconnected(DisconnectedEvent event) {
                //kick server...
                //System.out.println("cli disc: "+event.getReason());
                System.out.println("Logged out user "+ finalUserpass[0]);
                if(finalAuthService !=null) {
                    try {
                        finalAuthService.logout();
                    } catch (RequestException e) {
                        e.printStackTrace();
                    }
                }
                takenAccs.remove(finalAccIndex);
                srvSession.disconnect("");
                srvToCli.remove(srvSession, client);
            }
        });

        client.connect();

        return client;
    }
}