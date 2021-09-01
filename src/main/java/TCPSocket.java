import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Random;

public class TCPSocket {
    private final int MESSAGE_SIZE = 1024;
    private final DatagramSocket socket;
    private InetAddress clientInetAddress;
    private InetAddress serverInetAddress;
    private int serverPort;
    private int clientPort;
    private int serverSeqNumber = 1;

    public TCPSocket(int port, int timeout) throws SocketException {
        socket = new DatagramSocket(port);
        socket.setSoTimeout(timeout);
    }

    private TCPPacket convertToTCPPacket(@NotNull DatagramPacket packet) throws IOException, ClassNotFoundException {
        ByteArrayInputStream is = new ByteArrayInputStream(packet.getData());
        ObjectInputStream ois = new ObjectInputStream(is);
        return (TCPPacket) ois.readObject();
    }

    private @Nullable TCPPacket receivePacket() throws IOException, ClassNotFoundException {
        DatagramPacket packet = new DatagramPacket(new byte[MESSAGE_SIZE], MESSAGE_SIZE);
        try {
            socket.receive(packet);
        } catch (SocketTimeoutException e) {
            return null;
        }
        return convertToTCPPacket(packet);
    }

    public void send(@NotNull ArrayList<String> data) throws IOException, ClassNotFoundException {
        int ackNumber = new Random().nextInt() % 1000;
        int size = data.size();
        for (var i = 0; i < size; i++) {
            TCPPacket tcpPacket = new TCPPacket(ackNumber, serverSeqNumber, data.get(i));
            if (i == size - 1) {
                tcpPacket.setFin();
            }
            byte[] msg = tcpPacket.getBytes();
            socket.send(new DatagramPacket(msg, msg.length, clientInetAddress, clientPort));
            serverSeqNumber++;
        }

        int lastAckNumber = 1;  //Last confirmed
        TCPPacket packet;
        while (true) {
            packet = receivePacket();
            if (packet == null) {
                System.err.println("Server: detected packets loss, resending. First is " + lastAckNumber);
                resendPackets(data, ackNumber, lastAckNumber);
            } else {
                if (packet.isFin()) {
                    return;
                }
                int ack = packet.getAckNumber();
                //Если верный номер ACKа
                if (ack >= lastAckNumber + 1) {
                    System.err.println("Server: right ack " + ack);
                    lastAckNumber = ack - 1;
                } else {
                    System.err.println("Server: detected packets loss, resending. First is " + ack);
                    resendPackets(data, ackNumber, ack);
                }
            }
        }
    }

    private void resendPackets(@NotNull ArrayList<String> data, int ackNumber, int lastAckNumber) throws IOException {
        int size = data.size();
        for (int i = lastAckNumber - 1; i < size; i++) {
            TCPPacket tcpPacket = new TCPPacket(ackNumber, i + 1, data.get(i));
            if (i == size - 1) {
                tcpPacket.setFin();
            }
            byte[] msg = tcpPacket.getBytes();
            socket.send(new DatagramPacket(msg, msg.length, clientInetAddress, clientPort));
        }
    }

    private void sendAck(int ack, int seq, InetAddress address, int port) throws IOException {
        TCPPacket packet = new TCPPacket(ack, seq, "");
        byte[] msg = packet.getBytes();
        socket.send(new DatagramPacket(msg, msg.length, address, port));
    }

    public ArrayList<String> receive() throws IOException, ClassNotFoundException {
        TCPPacket packet;
        int lastAcked = 0;
        boolean isEnd = false;
        ArrayList<String> data = new ArrayList<>();
        //Для симуляции потери пакетов
        Random randomLoss = new Random();
        //
        do {
            packet = receivePacket();
            //Симуляция потери пакетов
            int loss = randomLoss.nextInt(11);
            if (loss < 3/*Accepting*/) {
                System.err.println("loss = " + loss);
                packet = receivePacket();
            }
            //
            if (packet != null) {
                System.err.println("Received: " + packet.getData());

                //Пакет пришел в свою очередь
                if (lastAcked + 1 == packet.getSeqNumber()) {
                    //saving data
                    if (data.size() < packet.getSeqNumber()) {
                        data.add(packet.getData());
                    } else {
                        data.set(packet.getSeqNumber() - 1, packet.getData());
                    }
                    lastAcked = data.size();
                } else {
                    System.err.println("Wrong packet");
                    //Пакет пришел не в свою очередь
                    //Отправляем АСК на требуемый, этот сохраняем
                    int seqNumber = packet.getSeqNumber();
                    //Пришел из следующий из очереди
                    if (seqNumber > data.size()) {
                        int requiredSpace = seqNumber - data.size();
                        for (int i = 0; i < requiredSpace; i++) {
                            data.add(null);
                        }
                    }
                    data.set(seqNumber - 1, packet.getData());
                }
                //Ищем первый неполученный
                for (int i = 0; i < data.size(); i++) {
                    if (data.get(i) == null) {
                        lastAcked = i; //   т.к тут нумерация с 0
                        break;
                    }
                }
                sendAck(lastAcked + 1, packet.getAckNumber(), serverInetAddress, serverPort);

                System.err.println("Data at this moment: " + data);
                if (lastAcked == packet.getSeqNumber() && packet.isFin()) {
                    isEnd = true;
                    TCPPacket tcpPacket = new TCPPacket(true, false, true);
                    byte[] msg = tcpPacket.getBytes();
                    socket.send(new DatagramPacket(msg, msg.length, serverInetAddress, serverPort));
                }
            }

        } while (!isEnd);


        return data;
    }

    //Client handshake
    public void connect(InetAddress serverAddress, int serverPort) throws Exception {
        this.serverInetAddress = serverAddress;
        this.serverPort = serverPort;
        TCPPacket tcpPacket = new TCPPacket(false, true, false);
        byte[] syn = tcpPacket.getBytes();
        socket.send(new DatagramPacket(syn, syn.length, serverInetAddress, this.serverPort));
        DatagramPacket packet = new DatagramPacket(new byte[MESSAGE_SIZE], MESSAGE_SIZE);
        socket.receive(packet);
        tcpPacket = convertToTCPPacket(packet);
        if (tcpPacket.isSyn() && tcpPacket.isAck()) {
            tcpPacket = new TCPPacket(true, false, false);
            byte[] ack = tcpPacket.getBytes();
            socket.send(new DatagramPacket(ack, ack.length, serverInetAddress, this.serverPort));
            System.err.println("Client handshake successfully completed!");
            return;
        }
        throw new Exception("Client handshake error");
    }

    //Server handshake
    public void accept() throws Exception {
        DatagramPacket packet = new DatagramPacket(new byte[MESSAGE_SIZE], MESSAGE_SIZE);
        socket.receive(packet);
        clientInetAddress = packet.getAddress();
        clientPort = packet.getPort();
        TCPPacket tcpPacket = convertToTCPPacket(packet);
        if (tcpPacket.isSyn()) {
            TCPPacket synAck = new TCPPacket(true, true, false);
            byte[] msg = synAck.getBytes();
            socket.send(new DatagramPacket(msg, msg.length, clientInetAddress, clientPort));
        } else {
            throw new Exception("Server handshake error");
        }
        socket.receive(packet);
        tcpPacket = convertToTCPPacket(packet);
        if (tcpPacket.isAck()) {
            System.err.println("Server handshake successfully completed!");
            return;
        }
        throw new Exception("Server handshake error");
    }
}
