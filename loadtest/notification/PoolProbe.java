import com.sun.tools.attach.VirtualMachine;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.BufferedReader;
import java.io.InputStreamReader;

class PoolProbe {

    public static void main(String[] args) throws Exception {
        VirtualMachine application = VirtualMachine.attach(args[0]);
        String address;
        try {
            address = application.startLocalManagementAgent();
        } finally {
            application.detach();
        }

        ObjectName pool = new ObjectName("com.zaxxer.hikari:type=Pool (notification-baseline)");
        try (var connection = JMXConnectorFactory.connect(new JMXServiceURL(address));
             var input = new BufferedReader(new InputStreamReader(System.in))) {
            MBeanServerConnection server = connection.getMBeanServerConnection();
            // Check the selected pool before allowing the workload to start.
            server.getAttribute(pool, "TotalConnections");
            System.out.println("ready");
            while (input.readLine() != null) {
                Object pending = server.getAttribute(pool, "ThreadsAwaitingConnection");
                Object active = server.getAttribute(pool, "ActiveConnections");
                Object idle = server.getAttribute(pool, "IdleConnections");
                System.out.printf("%s %s %s%n", pending, active, idle);
            }
        }
    }
}
