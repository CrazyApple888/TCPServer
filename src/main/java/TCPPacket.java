import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class TCPPacket implements Serializable {
    private int ackNumber;
    private int seqNumber;
    private boolean isAck;
    private boolean isSyn;
    private boolean fin;
    private String data = "";

    public TCPPacket(boolean isAck, boolean isSyn, boolean fin) {
        this.isAck = isAck;
        this.isSyn = isSyn;
        this.fin = fin;
    }

    public TCPPacket(int ackNumber, int seqNumber, String data) {
        this.ackNumber = ackNumber;
        this.seqNumber = seqNumber;
        if (data != null) {
            this.data = data;
        }
    }

    public int getAckNumber() {
        return ackNumber;
    }

    public int getSeqNumber() {
        return seqNumber;
    }

    public boolean isAck() {
        return isAck;
    }

    public boolean isSyn() {
        return isSyn;
    }

    public boolean isFin() {
        return fin;
    }

    public void setFin() {
        fin = true;
    }

    public byte[] getBytes() throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(os);
        oos.writeObject(this);
        System.out.println("Я сосу бибу");
        return os.toByteArray();
    }

    public String getData() {
        return data;
    }
}
