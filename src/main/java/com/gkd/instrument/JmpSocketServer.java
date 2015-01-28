package com.gkd.instrument;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import javax.swing.JOptionPane;

import org.apache.log4j.Logger;

import com.gkd.GKD;
import com.gkd.Global;
import com.gkd.instrument.callgraph.JmpType;
import com.peterswing.CommonLib;

public class JmpSocketServer implements Runnable {
	private int port;
	private boolean shouldStop;
	private ServerSocket serverSocket;
	FileWriter fstream;
	int noOfJmpRecordToFlush = 100;

	public static Connection conn;
	Statement stat;
	PreparedStatement pstmt = null;

	//	public static LinkedHashSet<String> segments = new LinkedHashSet<String>();
	//	private SimpleDateFormat dateformat1 = new SimpleDateFormat("HH:mm:ss.S");
	//	public static Vector<JmpData> jmpDataVector = new Vector<JmpData>();

	//		Transaction tx;

	public static Logger logger = Logger.getLogger(JmpSocketServer.class);

	//	ExecutorService executorService = Executors.newFixedThreadPool(1);

	public static void main(String args[]) {
		JmpSocketServer jmpSocketServer = new JmpSocketServer();
		jmpSocketServer.startServer(8765, new JmpTableModel());
		jmpSocketServer.stopServer();
	}

	public void startServer(int port, JmpTableModel jmpTableModel) {
		this.port = port;
		try {
			fstream = new FileWriter(Global.jmpLog, false);
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		try {
			Class.forName("org.h2.Driver");
			conn = DriverManager.getConnection("jdbc:h2:" + new File(".").getAbsolutePath() + "/jmpDB");
			stat = conn.createStatement();
			//stat.execute("create table table1(jmpDataId integer generated by default as identity, cs bigint not null, date timestamp, name varchar(255))");

		} catch (Exception e1) {
			e1.printStackTrace();
		}

		shouldStop = false;
		new Thread(this).start();

		//		while (serverSocket != null && !serverSocket.isBound()) {
		//			try {
		//				Thread.currentThread().sleep(500);
		//			} catch (InterruptedException e) {
		//				e.printStackTrace();
		//			}
		//		}

	}

	public void stopServer() {
		shouldStop = true;
		try {
			serverSocket.close();
			conn.close();
			//			if (session.isOpen()) {
			//				session.close();
			//			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void run() {
		logger.debug("Jmp server start at port " + port);

		try {
			serverSocket = new ServerSocket(port);

			while (!shouldStop) {
				Socket clientSocket = serverSocket.accept();
				DataInputStream in = new DataInputStream(clientSocket.getInputStream());
				DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());

				int physicalAddressSize = in.read();
				int segmentAddressSize = in.read();
				int whatSize = in.read();
				int registerSize = in.read();
				int segmentRegisterSize = in.read();

				int lineNo = 1;
				int rowSize = physicalAddressSize * 2 + whatSize + segmentAddressSize * 2 + registerSize * 8 + segmentRegisterSize * 6;

				int noOfRecordRead = 0;
				int deep = 0;

				while (!shouldStop) {
					byte startBytes[] = new byte[5];
					in.readFully(startBytes);
					String beacon = new String(startBytes);
					if (!beacon.equals("start")) {
						fstream.write("jmp socket - beacon error\n");
						fstream.flush();
						System.err.println("jmp socket error beacon!=start");
						System.exit(-1);
					}

					byte[] tempBytes = new byte[4];
					in.readFully(tempBytes);
					noOfJmpRecordToFlush = ByteBuffer.wrap(tempBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();

					long fromAddress[] = new long[noOfJmpRecordToFlush];
					long toAddress[] = new long[noOfJmpRecordToFlush];
					long what[] = new long[noOfJmpRecordToFlush];
					long segmentStart[] = new long[noOfJmpRecordToFlush];
					long segmentEnd[] = new long[noOfJmpRecordToFlush];

					long eax[] = new long[noOfJmpRecordToFlush];
					long ecx[] = new long[noOfJmpRecordToFlush];
					long edx[] = new long[noOfJmpRecordToFlush];
					long ebx[] = new long[noOfJmpRecordToFlush];
					long esp[] = new long[noOfJmpRecordToFlush];
					long ebp[] = new long[noOfJmpRecordToFlush];
					long esi[] = new long[noOfJmpRecordToFlush];
					long edi[] = new long[noOfJmpRecordToFlush];

					long es[] = new long[noOfJmpRecordToFlush];
					long cs[] = new long[noOfJmpRecordToFlush];
					long ss[] = new long[noOfJmpRecordToFlush];
					long ds[] = new long[noOfJmpRecordToFlush];
					long fs[] = new long[noOfJmpRecordToFlush];
					long gs[] = new long[noOfJmpRecordToFlush];
					byte bytes[] = new byte[noOfJmpRecordToFlush * rowSize];

					int deeps[] = new int[noOfJmpRecordToFlush];

					int byteRead = 0;
					while (byteRead < bytes.length) {
						int b = in.read(bytes, byteRead, bytes.length - byteRead);
						if (b < 0) {
							System.err.println("b<0");
							System.exit(-1);
						}
						byteRead += b;
					}

					noOfRecordRead += noOfJmpRecordToFlush;
					GKD.instrumentStatusLabel.setText("jump : " + String.format("%,d", noOfRecordRead));

					int offset = 0;
					offset += read(fromAddress, bytes, offset, physicalAddressSize);
					offset += read(toAddress, bytes, offset, physicalAddressSize);

					offset += read(what, bytes, offset, whatSize);

					offset += read(segmentStart, bytes, offset, segmentAddressSize);
					offset += read(segmentEnd, bytes, offset, segmentAddressSize);

					offset += read(eax, bytes, offset, registerSize);
					offset += read(ecx, bytes, offset, registerSize);
					offset += read(edx, bytes, offset, registerSize);
					offset += read(ebx, bytes, offset, registerSize);
					offset += read(esp, bytes, offset, registerSize);
					offset += read(ebp, bytes, offset, registerSize);
					offset += read(esi, bytes, offset, registerSize);
					offset += read(edi, bytes, offset, registerSize);

					offset += read(es, bytes, offset, segmentRegisterSize);
					offset += read(cs, bytes, offset, segmentRegisterSize);
					offset += read(ss, bytes, offset, segmentRegisterSize);
					offset += read(ds, bytes, offset, segmentRegisterSize);
					offset += read(fs, bytes, offset, segmentRegisterSize);
					offset += read(gs, bytes, offset, segmentRegisterSize);

					byte endBytes[] = new byte[3];
					in.readFully(endBytes);

					beacon = new String(endBytes);
					if (!beacon.equals("end")) {
						fstream.write("jmp socket - beacon error\n");
						fstream.flush();
						System.err.println("jmp socket error beacon!=end");
						System.exit(-1);
					}

					//					tx = session.beginTransaction();

					for (int x = 0; x < noOfJmpRecordToFlush; x++) {
						deeps[x] = deep;
						switch ((int) what[x]) {
						case 12:
							deep++;
							break;
						case 13:
							deep++;
							break;
						case 14:
							deep--;
							break;
						case 15:
							deep--;
							break;
						case 16:
							deep++;
							break;
						case 17:
							deep++;
							break;
						case 18:
							deep--;
							break;
						case 19:
							deep++;
							break;
						case 20:
							deep--;
							break;
						}
					}
					try {
						pstmt = conn
								.prepareStatement("insert into jmpData (jmpDataId, cs, date, deep, ds, eax, ebp, ebx, ecx, edi, edx, es, esi, esp, fromAddress, fromAddressDescription, fs, gs, lineNo, segmentEnd, segmentStart, ss, toAddress, toAddressDescription, what) values (null, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
					} catch (SQLException e1) {
						e1.printStackTrace();
					}

					for (int x = 0; x < noOfJmpRecordToFlush; x++) {
						try {
							JmpType w = null;
							switch ((int) what[x]) {
							case 10:
								w = JmpType.JMP;
								break;
							case 11:
								w = JmpType.JMP_INDIRECT;
								break;
							case 12:
								w = JmpType.CALL;
								break;
							case 13:
								w = JmpType.CALL_INDIRECT;
								break;
							case 14:
								w = JmpType.RET;
								break;
							case 15:
								w = JmpType.IRET;
								break;
							case 16:
								w = JmpType.INT;
								break;
							case 17:
								w = JmpType.SYSCALL;
								break;
							case 18:
								w = JmpType.SYSRET;
								break;
							case 19:
								w = JmpType.SYSENTER;
								break;
							case 20:
								w = JmpType.SYSEXIT;
								break;
							default:
								w = JmpType.unknown;
							}

							//						executorService.execute(new Runnable() {
							//							public void run() {
							//								JmpData jmpData = new JmpData(lineNo, new Date(), fromAddress[x], null, toAddress[x], null, w, segmentStart[x], segmentEnd[x], eax[x], ecx[x],
							//										edx[x], ebx[x], esp[x], ebp[x], esi[x], edi[x], es[x], cs[x], ss[x], ds[x], fs[x], gs[x], deeps[x]);
							//								session.save(jmpData);
							if (lineNo % 10000 == 0) {
								logger.debug("processed " + lineNo);
							}
							//							}
							//						});

							pstmt.setInt(1, 12345);
							pstmt.setDate(2, null);
							pstmt.setInt(3, 12345);
							pstmt.setInt(4, 12345);
							pstmt.setInt(5, 12345);
							pstmt.setInt(6, 12345);
							pstmt.setInt(7, 12345);
							pstmt.setInt(8, 12345);
							pstmt.setInt(9, 12345);
							pstmt.setInt(10, 12345);
							pstmt.setInt(11, 12345);
							pstmt.setInt(12, 12345);
							pstmt.setInt(13, 12345);
							pstmt.setInt(14, 12345);
							pstmt.setString(15, "cheung");
							pstmt.setInt(16, 12345);
							pstmt.setInt(17, 12345);
							pstmt.setInt(18, 12345);
							pstmt.setInt(19, 12345);
							pstmt.setInt(20, 12345);
							pstmt.setInt(21, 12345);
							pstmt.setInt(22, 12345);
							pstmt.setString(23, "peter");
							pstmt.setInt(24, 12345);

							// Add row to the batch.

							pstmt.addBatch();

							//						executorService.execute(new InsertJmpDataThread(lineNo, new Date(), fromAddress[x], null, toAddress[x], null, w, segmentStart[x], segmentEnd[x], eax[x],
							//								ecx[x], edx[x], ebx[x], esp[x], ebp[x], esi[x], edi[x], es[x], cs[x], ss[x], ds[x], fs[x], gs[x], deeps[x]));
							//								session.save(jmpData);

							lineNo++;
						} catch (SQLException e) {
							e.printStackTrace();
						}
					}
					//					if (session.isOpen() && session.isConnected()) {
					//						tx.commit();
					//					}
					try {
						logger.debug("before commit");
						pstmt.executeBatch();
					} catch (SQLException e) {
						e.printStackTrace();
					}
					logger.debug("commited to db " + lineNo);

					out.write("done".getBytes());
					out.flush();

				} // end while

				in.close();
				clientSocket.close();
			}
			serverSocket.close();
		} catch (BindException ex) {
			JOptionPane.showMessageDialog(null, "You have turn on the profiling feature but the port " + port + " is not available. Program exit", "Error",
					JOptionPane.ERROR_MESSAGE);
			System.exit(-1);
		} catch (IOException ex2) {
			ex2.printStackTrace();
		}
	}

	//	class InsertJmpDataThread implements Runnable {
	//		JmpData jmpData;
	//		int lineNo;
	//
	//		public InsertJmpDataThread(int lineNo, Date date, long fromAddress, String fromAddressDescription, long toAddress, String toAddressDescription, JmpType what,
	//				long segmentStart, long segmentEnd, long eax, long ecx, long edx, long ebx, long esp, long ebp, long esi, long edi, long es, long cs, long ss, long ds, long fs,
	//				long gs, int deep) {
	//			this.lineNo = lineNo;
	//			jmpData = new JmpData(lineNo, date, fromAddress, fromAddressDescription, toAddress, toAddressDescription, what, segmentStart, segmentEnd, eax, ecx, edx, ebx, esp, ebp,
	//					esi, edi, es, cs, ss, ds, fs, gs, deep);
	//
	//			if (lineNo % 10000 == 0) {
	//				logger.info("        processed " + lineNo);
	//			}
	//		}
	//
	//		@Override
	//		public void run() {
	//			try {
	//				//				synchronized (session) {
	//				//					session.save(jmpData);
	//				//				}
	//			} catch (Exception ex) {
	//				ex.printStackTrace();
	//				System.out.println("shit=" + lineNo);
	//				System.exit(-234);
	//			}
	//		}
	//	}

	int read(long arr[], byte bytes[], int offset, int size) throws IOException {
		int totalByteRead = 0;
		for (int x = 0; x < arr.length; x++) {
			long value = read(bytes, offset + (x * size), size);
			totalByteRead += size;
			arr[x] = value;
		}
		return totalByteRead;
	}

	long read(byte bytes[], int offset, int size) throws IOException {
		if (size == 8) {
			return CommonLib.get64BitsInt(bytes, offset);
		} else if (size == 4) {
			return CommonLib.getInt(bytes, offset);
		} else if (size == 2) {
			return CommonLib.getShort(bytes[offset], bytes[offset + 1]);
		} else {
			return bytes[offset];
		}
	}
}
