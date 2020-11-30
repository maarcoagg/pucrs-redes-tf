// Recebe um pacote de algum cliente
// Separa o dado, o endereco IP e a porta deste cliente
// Imprime o dado na tela

import java.io.*;
import java.net.*;

class UDPServer {
   public static void main(String args[])  throws Exception
   {
      
      final String sourceDir = System.getProperty("user.dir");
      byte[] connData = new byte[1024];
      boolean connected = false;
      DatagramPacket connPacket = new DatagramPacket(connData, connData.length);
      DatagramSocket serverSocket = new DatagramSocket(9876);  // UDP socket @ port 9876
      serverSocket.setSoTimeout(5000);
      InetAddress IPAddress;
      int port;

      /* Transmission Variables */
      final String separator = ";";
      String[] data = null;
      String msg = null;
      String flag = null;
      int cliSeq = -1;
      int cliAck = -1;
      int seq = -1;
      int ack = -1;

      int packetsReceived = 0;
      while(true)
      {    
         /**
          * Espera receber uma conexao OK
          * Estabelece conexao OK
          * Gera um arquivo .txt OK
          * Transfere arquivos TO-DO
          * Fecha conexao DOING
          */
         
         /* Aguarda receber conexao */         
         System.out.println("\nEscutando conexões...\n");
         serverSocket.receive(connPacket);
         
         IPAddress = connPacket.getAddress();
         port = connPacket.getPort();
      
         /* Exibe mensagem recebida */
         msg = cleanMessage(connPacket);
         System.out.println("Recebido de "+IPAddress+":"+port+" a mensagem ("+connPacket.getLength()+" bytes): "+msg);

         /* Se receber SYN, tenta 3-Way Handshake */    
         data = msg.split(separator);
         if (data.length == 2 && data[0].equals("SYN"))
         {
            serverSocket.setSoTimeout(5000);
            
            /* Envia SYN-ACK */
            cliSeq = Integer.parseInt(data[1]);
            flag = "SYN-ACK";
            seq = 0;
            ack = cliSeq + 1;

            msg = flag + separator + seq + separator + ack;
            connData = msg.getBytes();
            IPAddress = connPacket.getAddress();
            port = connPacket.getPort();
            connPacket = new DatagramPacket(connData, connData.length, IPAddress, port);
            
            System.out.println("\nEnviando para "+IPAddress+":"+port+" a mensagem ("+connData.length+" bytes): "+msg+".");
            serverSocket.send(connPacket);

            /* Recebe ACK */
            System.out.println("\nEsperando ACK...");
            serverSocket.receive(connPacket);

            /* Exibe mensagem recebida */
            msg = cleanMessage(connPacket);
            System.out.println("\nRecebido de "+IPAddress+":"+port+" a mensagem ("+connPacket.getLength()+" bytes): "+msg);

            data = msg.split(separator);
            if (data.length == 2 && data[0].equals("ACK"))
            {
               cliAck = Integer.parseInt(data[1]);
               if (cliAck == seq+1)
               {
                  System.out.println("\nConexao estabelecida!");
                  connected = true;
               }
            }
            else
            {
               System.out.println("\nConexao interrompida!");
               System.exit(1);
            }
         }
         else
         {
            System.err.println("SYN nao encontrado.");
            System.exit(1);
         }


         do
         {

         }while(connected);

         /* Gera um arquivo .txt */
         final String fileName = generateName();
         String absoluteFilePath = sourceDir + File.separator + "receive" + File.separator + fileName;
         FileOutputStream fos = new FileOutputStream(absoluteFilePath);
         System.out.println("Arquivo \""+fileName+"\" criado.");

         
         /* A quantidade de pacotes recebida está hard-coded */
         for (int i = 0; i < 80; i++)
         {
            /* Recebe pacote e escreve no arquivo .txt */
            serverSocket.receive(connPacket);
            packetsReceived++;

            IPAddress = connPacket.getAddress();
            port = connPacket.getPort();
            System.out.println("Recebido pacote #"+packetsReceived+" de "+IPAddress+":"+port+".");

            //String packetData = new String(receivePacket.getData());
            fos.write(connData);
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

   private static String cleanMessage(DatagramPacket p)
   {
      byte[] cleanData = new byte[p.getLength()];

      for (int i = 0; i < p.getLength(); i++)
         cleanData[i] = p.getData()[i];
      
      String cleanMessage = new String(cleanData);
      return cleanMessage;
   }
}