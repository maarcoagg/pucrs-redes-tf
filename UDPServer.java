// Recebe um pacote de algum cliente
// Separa o dado, o endereco IP e a porta deste cliente
// Imprime o dado na tela

import java.io.*;
import java.net.*;

class UDPServer {
   public static void main(String args[])  throws Exception
      {
         
         final String sourceDir = System.getProperty("user.dir");
         byte[] receiveData = new byte[300];
         DatagramSocket serverSocket = new DatagramSocket(9876);  // UDP socket @ port 9876
         DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

         int packetsReceived = 0;
         while(true)
         {
            /* Gera um nome aleatorio e aguarda receber pacotes */         
            final String fileName = generateName();
            System.out.println("Arquivo \""+fileName+"\" criado.");
            String absoluteFilePath = sourceDir + File.separator + "receive" + File.separator + fileName;
            FileOutputStream fos = new FileOutputStream(absoluteFilePath);
            System.out.println("Aguardando...");            

            for (int i = 0; i < 80; i++)
            {
               /* Recebe pacote e escreve no arquivo .txt */
               serverSocket.receive(receivePacket);
               packetsReceived++;

               InetAddress IPAddress = receivePacket.getAddress();
               int port = receivePacket.getPort();
               System.out.println("Recebido pacote #"+packetsReceived+" de "+IPAddress+":"+port+".");

               //String packetData = new String(receivePacket.getData());
               fos.write(receiveData);
            }
            
            fos.close();

            // o lint do java sempre apontava warning para 'serverSocket' e 'fos' 
            // pois os mesmos nunca eram fechados. portanto este trecho serve apenas
            //  para eliminar estes warnings.
            if (fileName == null)
               break;
         }
         serverSocket.close();
      }

      private static String generateName()
      {
         int max = 10000;
         int min = 1000;
         double random = Math.random() * (max  - min + 1) + min;
         int num = (int) random;
         StringBuilder sb = new StringBuilder("received_").
                              append(num).
                              append(".txt");
         
         return sb.toString();
      }
}