import java.net.InetAddress;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) {

        Runnable ser = () -> {
            try {
                TCPSocket server = new TCPSocket(123, 1000);
                server.accept();

                ArrayList<String> data = new ArrayList<>();
                data.add("The");
                data.add("thirty-three");
                data.add("thieves");
                data.add("thought");
                data.add("that");
                data.add("they");
                data.add("thrilled");
                data.add("the");
                data.add("throne");
                data.add("throughout");
                data.add("Thursday");
                data.add(".");
                System.out.println("Data before sending: ");
                System.out.println(data);
                server.send(data);
                System.out.println("Server finished");
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        };
        Runnable cl = () -> {
            try {
                TCPSocket client = new TCPSocket(234, 1000);
                client.connect(InetAddress.getByName("localhost"), 123);
                var data = client.receive();
                System.out.println("Data after all received: ");
                System.out.println(data);
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        };

        Thread server = new Thread(ser);
        Thread client = new Thread(cl);
        server.start();
        client.start();




    }
}
