java ChatNode 7000 aliceimport java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/*
 * ChatNode.java
 * Usage: java ChatNode <listenPort> <username>
 *
 * Simple peer-to-peer chat node that:
 * - Listens for incoming peer connections on listenPort
 * - Lets you connect to other peers with "connect host port"
 * - Broadcasts typed messages to all connected peers with "msg <text>"
 * - Shows incoming messages and keeps a local history
 * - Commands: connect, msg, peers, history, help, quit
 */

public class ChatNode {
    private final int listenPort;
    private final String username;
    private final ServerSocket serverSocket;
    private final ExecutorService pool = Executors.newCachedThreadPool();

    // thread-safe collections
    private final Map<Integer, PeerConnection> peers = new ConcurrentHashMap<>();
    private final List<String> history = Collections.synchronizedList(new ArrayList<>());
    private volatile int nextPeerId = 1;

    public ChatNode(int listenPort, String username) throws IOException {
        this.listenPort = listenPort;
        this.username = username;
        this.serverSocket = new ServerSocket(this.listenPort);
    }

    public void start() {
        System.out.println("[" + username + "] Listening on port " + listenPort);
        // accept incoming connections
        pool.submit(() -> {
            while (!serverSocket.isClosed()) {
                try {
                    Socket s = serverSocket.accept();
                    int id = nextPeerId++;
                    PeerConnection pc = new PeerConnection(id, s, this);
                    peers.put(id, pc);
                    pool.submit(pc);
                    System.out.println("Incoming connection accepted: peer#" + id + " - " + s.getRemoteSocketAddress());
                } catch (IOException e) {
                    if (!serverSocket.isClosed()) {
                        System.out.println("Error accepting connection: " + e.getMessage());
                    }
                }
            }
        });

        // CLI loop
        try (Scanner sc = new Scanner(System.in)) {
            printHelp();
            while (true) {
                System.out.print("> ");
                String line = sc.nextLine();
                if (line == null) break;
                line = line.trim();
                if (line.isEmpty()) continue;

                if (line.startsWith("connect ")) {
                    String[] parts = line.split("\\s+");
                    if (parts.length < 3) {
                        System.out.println("Usage: connect <host> <port>");
                        continue;
                    }
                    String host = parts[1];
                    int port = Integer.parseInt(parts[2]);
                    connectToPeer(host, port);
                } else if (line.startsWith("msg ")) {
                    String msg = line.substring(4).trim();
                    if (msg.isEmpty()) continue;
                    broadcastMessage(msg);
                    String local = formatMessage(username, "me", msg);
                    history.add(local);
                    System.out.println(local);
                } else if (line.equals("peers")) {
                    listPeers();
                } else if (line.equals("history")) {
                    dumpHistory();
                } else if (line.equals("help")) {
                    printHelp();
                } else if (line.equals("quit")) {
                    shutdown();
                    break;
                } else {
                    System.out.println("Unknown command. Type 'help' for commands.");
                }
            }
        } catch (NoSuchElementException e) {
            // stdin closed
            shutdown();
        }
    }

    private void printHelp() {
        System.out.println("Commands:");
        System.out.println(" connect <host> <port>   - connect to a peer");
        System.out.println(" msg <text>              - send message to all peers");
        System.out.println(" peers                   - list connected peers");
        System.out.println(" history                 - show local chat history");
        System.out.println(" help                    - this help");
        System.out.println(" quit                    - exit");
        System.out.println();
    }

    private void connectToPeer(String host, int port) {
        pool.submit(() -> {
            try {
                Socket s = new Socket();
                s.connect(new InetSocketAddress(host, port), 5000);
                int id = nextPeerId++;
                PeerConnection pc = new PeerConnection(id, s, this);
                peers.put(id, pc);
                pool.submit(pc);
                System.out.println("Connected to peer#" + id + " - " + host + ":" + port);
            } catch (IOException e) {
                System.out.println("Failed to connect to " + host + ":" + port + " - " + e.getMessage());
            }
        });
    }

    void removePeer(int id) {
        peers.remove(id);
        System.out.println("Peer#" + id + " disconnected");
    }

    private void listPeers() {
        if (peers.isEmpty()) {
            System.out.println("No connected peers.");
            return;
        }
        System.out.println("Connected peers:");
        peers.forEach((id, pc) -> {
            System.out.println(" #" + id + " - " + pc.getRemoteAddress());
        });
    }

    private void dumpHistory() {
        synchronized (history) {
            if (history.isEmpty()) {
                System.out.println("(no history)");
                return;
            }
            System.out.println("Chat history:");
            for (String h : history) {
                System.out.println(h);
            }
        }
    }

    private void broadcastMessage(String msg) {
        String payload = username + ": " + msg;
        peers.values().forEach(pc -> pc.sendLine(payload));
    }

    void receiveMessage(int peerId, String line) {
        String formatted = formatMessage("peer#" + peerId, peers.get(peerId).getRemoteAddress(), line);
        history.add(formatted);
        System.out.println(formatted);
    }

    private String formatMessage(String who, String fromAddr, String text) {
        return "[" + who + "@" + fromAddr + "] " + text;
    }

    private void shutdown() {
        System.out.println("Shutting down...");
        try { serverSocket.close(); } catch (IOException ignored) {}
        peers.values().forEach(PeerConnection::close);
        pool.shutdownNow();
        System.out.println("Bye.");
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: java ChatNode <listenPort> <username>");
            System.out.println("Example: java ChatNode 7000 alice");
            return;
        }
        int port = Integer.parseInt(args[0]);
        String user = args[1];
        ChatNode node = new ChatNode(port, user);
        node.start();
    }
}

class PeerConnection implements Runnable {
    private final int id;
    private final Socket socket;
    private final ChatNode owner;
    private volatile boolean running = true;
    private BufferedWriter writer;
    private BufferedReader reader;

    PeerConnection(int id, Socket socket, ChatNode owner) {
        this.id = id;
        this.socket = socket;
        this.owner = owner;
        try {
            this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getRemoteAddress() {
        return socket.getRemoteSocketAddress().toString();
    }

    public void sendLine(String s) {
        try {
            writer.write(s);
            writer.write("\n");
            writer.flush();
        } catch (IOException e) {
            // treat as disconnect
            close();
        }
    }

    @Override
    public void run() {
        // First start a reader loop
        try {
            String line;
            while (running && (line = reader.readLine()) != null) {
                owner.receiveMessage(id, line);
            }
        } catch (IOException e) {
            // ignore - close below
        } finally {
            close();
            owner.removePeer(id);
        }
    }

    public void close() {
        running = false;
        try { socket.close(); } catch (IOException ignored) {}
    }
}
