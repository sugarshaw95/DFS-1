package nodetest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.Enumeration;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.barchart.udt.ExceptionUDT;
import com.barchart.udt.SocketUDT;
import com.barchart.udt.TypeUDT;
import com.barchart.udt.net.NetInputStreamUDT;
import com.barchart.udt.net.NetOutputStreamUDT;

/**
 * 
 */

/**
 * @author wzy, ydz
 *
 */
public class Node {

	protected String ID;
	protected String user_name;
	protected Set<String> nodeIDs;
	protected Map<String, String> UName_ID;
	protected Thread node_thread;
        protected Map<Integer, DataReceiver> data_receiver;
        protected Map<Integer, Map<String, String>> data_resend;
        
	protected String IP_local;
	protected Map<String, String> node_IPs;

	protected Queue<String> node_inserted_lm;
	protected Queue<String> node_deleted_lm;

	protected Thread link_connectivity_checker;
	protected Thread link_establisher_thread_s1;
	protected Thread link_establisher_thread_s2;
	protected LinkEstablisher link_establisher;
	protected Map<String, ReentrantLock> link_establish_locks;
	protected Map<String, SocketUDT> link_establish_socks;
	protected Map<String, ReentrantLock> link_locks;
	protected Map<String, Timer> link_timers;

	protected InetAddress IP_local_server;
	protected int Port_local_server;
	protected String server_host;
	protected int server_port;
	protected InputStream in_s;
	protected OutputStream out_s;
	protected Thread server_link_thread;
	protected Map<String, Queue<Map<String, String>>> messages_from_server;

	protected ExecutorService data_to_send;

	/**
	 * @param user_name the user name of the node
	 * @param server_host the IP address of the server
	 * @param server_port the port of the server
	 * @throws ExceptionUDT failed to create socket with server and transfer message
	 * @throws NodeException the structure of the package is wrong
	 * @throws PackException the structure of the package is wrong
	 */
	public Node(String user_name, String server_host, int server_port)
        			throws ExceptionUDT, PackException, NodeException {
		this.user_name = user_name;
                this.data_receiver = new ConcurrentHashMap<>();
                this.data_resend = new ConcurrentHashMap<>();
		nodeIDs = ConcurrentHashMap.<String> newKeySet();
		UName_ID = new ConcurrentHashMap<>();
		node_IPs = new ConcurrentHashMap<>();
		node_inserted_lm = new ConcurrentLinkedQueue<>();
		node_deleted_lm = new ConcurrentLinkedQueue<>();
		link_establisher = new LinkEstablisher(this);
		link_establish_locks = new ConcurrentHashMap<>();
		link_establish_socks = new ConcurrentHashMap<>();
		link_locks = new ConcurrentHashMap<>();
		link_timers = new ConcurrentHashMap<>();
		messages_from_server = new ConcurrentHashMap<>();
		messages_from_server.put("Node", new ConcurrentLinkedQueue<Map<String, String>>());
		messages_from_server.put("Link", new ConcurrentLinkedQueue<Map<String, String>>());
		messages_from_server.put("Data", new ConcurrentLinkedQueue<Map<String, String>>());
		messages_from_server.put("ERR", new ConcurrentLinkedQueue<Map<String, String>>());
		this.server_host = server_host;
		this.server_port = server_port;
		data_to_send = Executors.newCachedThreadPool();
		try {
			IP_local = getRealLocalIP();
			System.out.println(IP_local);
		} catch (UnknownHostException e) {
			IP_local = null;
			e.printStackTrace();
		}
		byte arr[] = new byte[4096];
		String str = null;
		Map<String, String> pac;
		SocketUDT server = new SocketUDT(TypeUDT.STREAM);
		server.setBlocking(true);
		server.connect(new InetSocketAddress(this.server_host, this.server_port));
		IP_local_server = server.getLocalInetAddress();
		Port_local_server = server.getLocalInetPort();
		in_s = new NetInputStreamUDT(server);// currently not in use
		out_s = new NetOutputStreamUDT(server);// currently not in use
		pac = new ConcurrentHashMap<String, String>();
		pac.put("Insertion", "Insertion");
		try {
			str = Packer.pack("NodeI", "00", pac);
		} catch (PackException e) {// just used for debug
			e.printStackTrace();
		}
		server.send(str.getBytes(Charset.forName("ISO-8859-1")));// Insertion
		server.receive(arr);
		str = new String(arr, Charset.forName("ISO-8859-1")).trim();
		Node.empty_arr(str.length(), arr);
		pac = Packer.unpack(str);
		ID = pac.get("ID");
		pac = new ConcurrentHashMap<>();
		pac.put("UName", this.user_name);
		pac.put("LIP", this.IP_local);
		try {
			str = Packer.pack("NodeI", "03", pac);
		} catch (PackException e) {// just used for debug
			e.printStackTrace();
		}
		server.send(str.getBytes(Charset.forName("ISO-8859-1")));// send user
																	// name
		server.receive(arr);// receive node and user name table
		str = new String(arr, Charset.forName("ISO-8859-1")).trim();
		pac = Packer.Check_table(str);
		int cnt = Integer.parseInt(pac.get("cnt"));
		String s1 = "UName_";
		String s3 = "LIP_";
		String s2 = "ID_";
		for (int i = 1; i <= cnt; i++) {
			nodeIDs.add(pac.get(s2 + i));
			UName_ID.put(pac.get(s1 + i), pac.get(s2 + i));
			node_IPs.put(pac.get(s2 + i), pac.get(s3 + i));
			link_establish_locks.put(pac.get(s2 + i), new ReentrantLock());
		}
		link_connectivity_checker = new Thread(new LinkConnectivityChecker(this));
		link_connectivity_checker.start();
		link_establisher_thread_s1 = new Thread(new LinkEstablisherThreadS1(this));
		link_establisher_thread_s1.start();
		link_establisher_thread_s2 = new Thread(new LinkEstablisherThreadS2(this));
		link_establisher_thread_s2.start();
		node_thread = new Thread(new NodeThread(this));
		node_thread.start();
		server_link_thread = new Thread(new ServerLink(this));
		server_link_thread.start();
	}

	/**
         * static method to empty the array after received info
	 * @param length the info's length
	 * @param arr the array that received info
	 */
	protected static void empty_arr(int length, byte arr[]) {
		for (int i = 0; i < length; i++) {
			arr[i] = ' ';
		}
	}

	/**
	 * @param str
	 * @param ID_p
	 * @return result
	 */
	/*
	 * public Future<Boolean> transfer_message(String str, String ID_p) { if
	 * (str == null || str.isEmpty()) { throw new IllegalArgumentException(
	 * "Empty String."); } if (nodeIDs.contains(ID_p)) {// Launch a new task and
	 * hold its result Future<Boolean> result; result = data_to_send.submit(new
	 * DataSender(this, ID_p, str)); return result; } else { throw new
	 * IllegalArgumentException("The destination isn't exist."); } }
	 */

	/**
         * method to transfer data between nodes
	 * @param obj object that implements Serializable interface and to be sent
	 * @param peer the user name of the destination
	 * @return result true stands for transfer success
	 * @throws IOException object doesn't implement Serializable interface
	 */
	public Future<Boolean> transfer_message(Object obj, String peer) throws IOException {
		if (obj == null) {
			throw new IllegalArgumentException("Null Object.");
		}
		ByteArrayOutputStream bo = new ByteArrayOutputStream();
		ObjectOutputStream so = new ObjectOutputStream(bo);
		so.writeObject(obj);
		so.flush();
		String str = new String(bo.toByteArray(), Charset.forName("ISO-8859-1"));
		so.close();
		bo.close();
		String ID_p = UName_ID.get(peer);
		if (ID_p != null && nodeIDs.contains(ID_p)) {// Launch a new task and
														// hold its result
			Future<Boolean> result;
			result = data_to_send.submit(new DataSender(this, ID_p, str));
                        Logger.getLogger(Node.class.getName()).log(Level.CONFIG, "Sending starts.");
			return result;
		} else {
			throw new IllegalArgumentException("The destination does not exist.");
		}
	}

	/**
	 * @return LocalIP
	 * @throws UnknownHostException
	 */
	private String getRealLocalIP() throws UnknownHostException {
		StringBuilder IFCONFIG = null;
		if (System.getProperties().getProperty("os.name").toLowerCase().contains("win")) {
			return InetAddress.getLocalHost().getHostAddress();
		} else {
			IFCONFIG = new StringBuilder();
			try {
				for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en
						.hasMoreElements();) {
					NetworkInterface intf = en.nextElement();
					for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
						InetAddress inetAddress = enumIpAddr.nextElement();
						if (!inetAddress.isLoopbackAddress() && !inetAddress.isLinkLocalAddress()
								&& inetAddress.isSiteLocalAddress()) {
							IFCONFIG.append(inetAddress.getHostAddress().toString());
						}
					}
				}
			} catch (SocketException ex) {
				Logger.getLogger(Node.class.getName()).log(Level.SEVERE, null, ex);
			}
		}
		return IFCONFIG.toString();
	}
}
