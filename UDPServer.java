// Recebe um pacote de algum cliente
// Separa o dado, o endereco IP e a porta deste cliente
// Imprime o dado na tela

import java.io.*;
import java.net.*;

class UDPServer {
   public static void main(String args[])  throws Exception
      {
         // guarda nome dos arquivos para montar os paths
         final String fileName = "received_file.txt";
         final String srcDir = System.getProperty("user.dir");

         // monta path dos arquivos a serem enviados
         String absoluteFilePath = srcDir + File.separator + "receive" + File.separator + fileName;

         // cria socket do servidor com a porta 9876
         DatagramSocket serverSocket = new DatagramSocket(9876);

         byte[] receiveData = new byte[10*1024];

         FileOutputStream fos = new FileOutputStream(absoluteFilePath);

         while(true)
         {
            System.out.println("Aguardando...");

            // declara o pacote a ser recebido
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

            // recebe o pacote do cliente
            serverSocket.receive(receivePacket);

            // pega os dados, o endereco IP e a porta do cliente
            // para poder mandar a msg de volta
            String fileData = new String(receivePacket.getData());
            InetAddress IPAddress = receivePacket.getAddress();
            int port = receivePacket.getPort();

            fos.write(receiveData);

            System.out.println("Mensagem recebida: " + fileData);
            System.out.println("IP Address: " + IPAddress + "\tPort: "+port);

            // o lint do java sempre apontava warning para 'serverSocket' e 'fos' 
            // pois os mesmos nunca eram fechados. portanto este trecho serve apenas
            //  para eliminar estes warnings.
            if (fileData.equals(null))
               break;
         }
         serverSocket.close();
         fos.close();
      }
}