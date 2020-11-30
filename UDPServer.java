// Recebe um pacote de algum cliente
// Separa o dado, o endereco IP e a porta deste cliente
// Imprime o dado na tela

import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.CRC32;
import java.security.MessageDigest;

class UDPServer {

   static CRC32 crc = new CRC32();
   static DatagramSocket socket; // UDP socket
   static InetAddress ip; // IP cliente
   static int port;  // Port cliente
   static String checksum;
   static String filePath;
   final static String separator = ";";
   final static String sourceDir = System.getProperty("user.dir");

   public static void main(String args[])  throws Exception
   {      
      socket = new DatagramSocket(9876);
      boolean connected = false;
      Map<Integer,byte[]> fileMap = new HashMap<>();
      FileOutputStream fos = null;

      while(true)
      {    
         /* Estabelece conexão */
         connected = tryConnect();
         if (connected)
         {
            /* Gera um arquivo .txt */
            fos = createFile();

            String msg = null;
            long crcVal;
            int ack = 0;
            do
            {
               DatagramPacket recvPacket = receive(msg);
               msg = cleanMessage(recvPacket);
               String[] recvData = msg.split(separator);
               String flag = recvData[0];
               switch (flag)
               {
                  case "DATA":
                  int seq = Integer.parseInt(recvData[1]);
                  if (ack == seq)
                  {
                     byte[] data = recvData[3].getBytes();
                     crc.update(data);
                     crcVal = crc.getValue();
                     System.out.print("\nPacote #"+ack+" recebido ("+data.length+" bytes). CRC: "+crcVal);
                     if (crcVal == Long.parseLong(recvData[2]))
                     {
                        System.out.println("\t(OK).");
                        fileMap.put(ack, data);
                        ack++;
                        msg = "ACK" + separator + ack;
                        send(msg);
                     } else System.out.println("\t(NOT-OK).");  
                  }
                  else
                  {
                     System.err.println("Pacote esperado: "+ack+"\t Pacote recebido: "+seq);
                     msg = "ACK" + separator + ack;
                     send(msg);
                  }
                  break;
                  case "CHECKSUM":
                     checksum = recvData[1];
                     break;
                  case "FIN": /* Finaliza conexão */
                  connected = tryDisconnect(Integer.parseInt(recvData[1]));
                  break;
                  default:
                  System.err.println("Flag inválida: "+flag+". Pacote descartado.");
               }
            } while (connected);
         }

         if (fileMap.size() > 0)
         {
            /* Remonta arquivo original */
            for(int i = 0; i < fileMap.size(); i++)
            {
               byte[] data = fileMap.get(i);
               fos.write(data);
            }
            fileMap = new HashMap<>();

            boolean equalChecksum = verifyChecksum();
            if (equalChecksum)  
               System.out.println("Checksum OK.");
            else System.out.println("Checksum NOT-OK.");
         }
      }
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
      if (p.getLength() < 300)
         System.out.println("\nRecebido de "+ip+":"+port+" a mensagem ("+p.getLength()+" bytes): "+cleanMessage);
      else
         System.out.println("Recebido "+p.getLength()+" bytes de "+ip+":"+port+".");
      return cleanMessage;
   }

   private static void send(String msg)
   {
      byte[] sendData = msg.getBytes();
      DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ip, port);

      System.out.println("\nEnviando para "+ip+":"+port+" ("+sendData.length+" bytes): "+msg+".");
      try{
         socket.send(sendPacket);
      } catch (Exception e) {
         System.err.println("ERRO: Não foi possível enviar pacote");
         System.err.println(e);
      }
   }

   /**
    * Recebe pacote UDP de até 1 Kb. Retransmite 'msg' caso uma exceção ocorra.
    * @param msg Mensagem a ser retransmitida
    * @return  Pacote UDP recebido.
    */
    private static DatagramPacket receive(String msg)
    {
      DatagramPacket recvPacket = null;
      byte[] recvData = new byte[1024];
      
      recvPacket = new DatagramPacket(recvData, recvData.length);
      try {
         socket.receive(recvPacket);
      } catch (SocketTimeoutException e) {
         if (msg != null)
         {
            System.err.println("TIMEOUT: Retransmitindo mensagem.");
            send(msg);
         } else System.err.println("TIMEOUT: "+e);
      } catch (IOException e){
         if (msg != null)
         {
            System.err.println("I/O: Retransmitindo mensagem");
            send(msg);
         } else System.err.println("I/O: "+e);
      }
      return recvPacket;
    }

   private static boolean tryConnect()
    {
      boolean connected = false;
      byte[] recvData = new byte[1024];
      DatagramPacket recvPacket = new DatagramPacket(recvData, recvData.length);

      System.out.println("\nAguardando por conexões...\n");
      setTimeout(180000); // 180.000ms = 3 min
      try{
         socket.receive(recvPacket);
      } catch(IOException e) {
         System.err.println("I/O: "+e);
         System.exit(1);
      } 

      ip = recvPacket.getAddress();
      port = recvPacket.getPort();
      setTimeout(5000);
      
      /* Limpa e exibe mensagem recebida */
      String msg = cleanMessage(recvPacket);

      /* Se receber SYN, tenta 3-Way Handshake */    
      String[] data = msg.split(separator);
      if (data.length == 2 && data[0].equals("SYN"))
      {
         try {
            socket.setSoTimeout(5000);
         } catch (SocketException e) {
            System.err.println("ERRO: "+e);
            System.exit(1);
         }

         /* Envia SYN-ACK */
         int cliSeq = Integer.parseInt(data[1]); //Client SEQ
         int seq = 0;
         int ack = cliSeq + 1;
         msg = "SYN-ACK" + separator + seq + separator + ack;
         send(msg);

         /* Aguarda ACK do cliente */
         System.out.println("\nEsperando ACK...");
         recvPacket = receive(msg);

         /* Limpa e verifica se recebeu ACK corretamente */
         msg = cleanMessage(recvPacket);
         data = msg.split(separator);
         if (data.length == 2 && data[0].equals("ACK") && Integer.parseInt(data[1]) == seq+1)
         {
            System.out.println("\nConexao estabelecida!");
            connected = true;
         } else System.out.println("Resposta ACK inválida do cliente.\n");
      } else System.err.println("Resposta SYN inválida do cliente.\n");

      return connected;
    }

   private static boolean tryDisconnect(int seq)
   {
      boolean connected = true;
      String msg = null;
      int attempts = 1; 

      do
      {
         /* Envia ACK */
         int ack = seq+1;
         msg = "ACK"+separator+ack;
         send(msg);

         //sleep

         /* Envia FIN-ACK */
         seq = 0;
         msg = "FIN-ACK"+separator+seq+separator+ack;
         send(msg);

         /* Aguarda resposta do servidor */
         System.out.println("\nEsperando ACK...");
         DatagramPacket recvPacket = receive(msg);
         msg = cleanMessage(recvPacket);
         String[] data = msg.split(separator);

         if (data.length == 2 && data[0].equals("ACK") && Integer.parseInt(data[1]) == seq+1)
         {
            System.out.println("Conexão encerrada.\n");
            connected = false;
         } else System.err.println("Resposta ACK inválida do servidor!\n");
         attempts++;
      } while (connected && attempts < 3);
      
      return connected;
   }

   private static FileOutputStream createFile()
   {
      final String fileName = generateName();
      final String absoluteFilePath = sourceDir + File.separator + "receive" + File.separator + fileName;
      filePath = absoluteFilePath;
      FileOutputStream fos;
      try {
         fos = new FileOutputStream(absoluteFilePath);
         System.out.println("Arquivo \""+fileName+"\" criado.");
      } catch (FileNotFoundException e) {
         System.err.println("ERRO: "+e);
         fos = null;
      }      
      return fos;
   }

   private static void setTimeout(int ms)
   {
      try {
         socket.setSoTimeout(ms);
      } catch (SocketException e) {
         System.err.println("SOCKET: "+e);
         System.exit(1);
      }
   }

   private static String getFileChecksum(MessageDigest digest, File file) throws IOException
   {
      FileInputStream fis = new FileInputStream(file);
      
      byte[] byteArray = new byte[1024];
      int bytesCount = 0; 
         
      while ((bytesCount = fis.read(byteArray)) != -1) {
         digest.update(byteArray, 0, bytesCount);
      }
      
      fis.close();
      
      byte[] bytes = digest.digest();
      
      StringBuilder sb = new StringBuilder();
      for(int i=0; i< bytes.length ;i++)
      {
         sb.append(Integer.toString((bytes[i] & 0xff) + 0x100, 16).substring(1));
      }
      
      //return complete hash
      return sb.toString();
   }

   private static boolean verifyChecksum()
   {
      boolean equal = false;
      try{
         MessageDigest md5Digest = MessageDigest.getInstance("MD5");
         String thisChecksum = getFileChecksum(md5Digest,new File(filePath));
         System.out.println("Checksum gerado: "+thisChecksum);
         equal = checksum.equals(thisChecksum);
      } catch (Exception e) {
         System.err.println("CHECKSUM: "+e);
         System.exit(1);
      }

      return equal;
   }
}