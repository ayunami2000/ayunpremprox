package me.ayunami2000.ayunpremprox;

import com.github.steveice10.mc.auth.exception.request.RequestException;
import com.github.steveice10.mc.auth.service.AuthenticationService;
import com.github.steveice10.mc.auth.service.MojangAuthenticationService;
import com.github.steveice10.mc.auth.service.MsaAuthenticationService;
import com.github.steveice10.mc.auth.service.SessionService;
import com.github.steveice10.mc.protocol.MinecraftConstants;
import com.github.steveice10.mc.protocol.MinecraftProtocol;
import com.github.steveice10.mc.protocol.ServerLoginHandler;
import com.github.steveice10.mc.protocol.packet.handshake.serverbound.ClientIntentionPacket;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class Main {
    private static Map<Session,Session> srvToCli = new HashMap<>();
    private static List<String[]> accs = new ArrayList<>();
    private static List<Integer> takenAccs = new ArrayList<>();
    private static boolean isCracked=false;
    private static boolean forwardUsername=false;

    public static void main(String[] args){
        try {
            String[] accList = new String(Files.readAllBytes(Path.of("alts.txt"))).trim().replaceAll("\\r","").split("\n");
            for (String acc : accList) {
                accs.add(acc.split(":",2));
            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        if(args.length>0&&args[0].equalsIgnoreCase("cracked"))isCracked=true;
        if(args.length>0&&args[0].equalsIgnoreCase("forward"))isCracked=forwardUsername=true;

        SessionService sessionService = new SessionService();
        sessionService.setProxy(Proxy.NO_PROXY);

        Server server = new TcpServer("127.0.0.1", args.length<2?25569:Integer.parseInt(args[1]), MinecraftProtocol::new);
        server.setGlobalFlag(MinecraftConstants.SESSION_SERVICE_KEY, sessionService);
        server.setGlobalFlag(MinecraftConstants.VERIFY_USERS_KEY, false);
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
                cliSession.disconnect("");
                srvToCli.remove(event.getSession(), cliSession);
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

        if(isCracked){
            userpass[0]=forwardUsername?crackedUsername:getSaltString(10);
        }else {
            while (takenAccs.contains(accIndex)) accIndex++;
            if (accs.size() <= accIndex) {
                return null;//out of accounts :(
            }
            takenAccs.add(accIndex);
            userpass = accs.get(accIndex);
        }

        MinecraftProtocol protocol;
        try {
            /*
            AuthenticationService authService = new MsaAuthenticationService("ayunpremprox"+Math.random());
            authService.setUsername(args[0]);
            authService.setPassword(args[1]);
            authService.setProxy(Proxy.NO_PROXY);
            authService.login();
            */

            if(isCracked){
                protocol = new MinecraftProtocol(userpass[0]);
            }else {
                AuthenticationService authService;
                if(userpass[0].startsWith("<MS> ")){
                    authService = new MsaAuthenticationService("ayunpremprox"+Math.random());
                    authService.setUsername(userpass[0].substring(5));
                    authService.setPassword(userpass[1]);
                    authService.setProxy(Proxy.NO_PROXY);
                    authService.login();
                }else {
                    authService = new MojangAuthenticationService();
                    authService.setUsername(userpass[0]);
                    authService.setPassword(userpass[1]);
                    authService.setProxy(Proxy.NO_PROXY);
                    authService.login();
                }

                protocol = new MinecraftProtocol(authService.getSelectedProfile(), authService.getAccessToken());
            }
            System.out.println("Logged in user "+userpass[0]);
        } catch (RequestException e) {
            e.printStackTrace();
            return null;
        }

        SessionService sessionService = new SessionService();
        sessionService.setProxy(Proxy.NO_PROXY);

        Session client = new TcpClientSession(args.length<3?"mh-prd.minehut.com":args[2], args.length<4?25565:Integer.parseInt(args[3]), protocol, null);
        client.setFlag(MinecraftConstants.SESSION_SERVICE_KEY, sessionService);
        int finalAccIndex = accIndex;
        String[] finalUserpass = userpass;
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
                takenAccs.remove(finalAccIndex);
                srvSession.disconnect("");
                srvToCli.remove(srvSession, client);
            }
        });

        client.connect();

        return client;
    }
}
