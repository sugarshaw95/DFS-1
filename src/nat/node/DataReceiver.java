/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nodetest;

import com.barchart.udt.ExceptionUDT;
import com.barchart.udt.SocketUDT;
import com.barchart.udt.TypeUDT;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 
 * @author 杨德中
 */
public class DataReceiver implements Runnable
{
    protected String from;
    protected int cnt;
    protected int No;
    protected String res;
    protected Map<Integer, String> pack;
    protected Timer timer;
    protected Node node;
    /**
     * create a data receiver
     * @param from the ID of starting node
     * @param cnt the amount of the packages
     * @param No the number of the package
     * @param node the node itself
     */
    public DataReceiver(String from, int cnt, int No, Node node)
    {
        this.node = node;
        this.from = from;
        this.cnt = cnt;
        this.No = No;
        this.res = "";
        this.pack = new ConcurrentHashMap<>();
        this.timer = new Timer(60000); //1分钟?
    }
    
    /**
     * receive data packages, if the packages are not complete in 60s, the node will ask the server to inform the starting node to resend.
     */
    @Override
    public void run()
    {
        this.timer.start();
        while(this.pack.size() < this.cnt && (!this.timer.isExpired()))
        {
        }    
        System.out.println(this.pack.size() + ":" + this.cnt);
        if(this.pack.size() < this.cnt)
        {
            Map<String, String> info = new HashMap<>();
            info.put("ID", from);
            info.put("ID_target", node.ID);
            info.put("No", No + "");
            info.put("Cnt", cnt + "");
            SocketUDT sock;
            try {
                sock = new SocketUDT(TypeUDT.STREAM);
                sock.connect(new InetSocketAddress(node.server_host, node.server_port));
                sock.send(Packer.pack("DataR", info).getBytes(Charset.forName("ISO-8859-1")));
            }
            catch (ExceptionUDT | PackException ex) 
            {
                Logger.getLogger(DataReceiver.class.getName()).log(Level.SEVERE, null, ex);
            }
            return;
        }
        for(int i = 0; i < this.cnt; ++i)
        {
            res = res + this.pack.get(i);
        }
        ObjectInputStream oin = null;
        Request obj = null;
        try 
        {
            //得到完整内容
            oin = new ObjectInputStream(new ByteArrayInputStream(res.getBytes(Charset.forName("ISO-8859-1"))));
            obj = (Request)oin.readObject();
            System.out.println(obj.getRequestType() + "\n" + obj.getRequestMethod() + "\n" + obj.getReplyTo());        
        } 
        catch (IOException ex) 
        {
//            Logger.getLogger(DataReceiver.class.getName()).log(Level.SEVERE, null, ex);
            System.out.println("get object failed...");
        } 
        catch (ClassNotFoundException ex) 
        {
            Logger.getLogger(DataReceiver.class.getName()).log(Level.SEVERE, null, ex);
        }
        //ReqHandler.handle(obj);
    }

}
