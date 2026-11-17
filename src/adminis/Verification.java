package adminis;
import java.sql.*;

import conn.Connec;

public class Verification {
	ResultSet rst;
	Statement st;
	Connec cn=new Connec();
	
	public boolean verifier(String titre){
		String qr="select 1 from livre where titre=? and disponible='OUI'";
		try{
			PreparedStatement ps=cn.connecion().prepareStatement(qr);
			ps.setString(1,titre);
			rst=ps.executeQuery();
			return rst.next();
		}
		catch(SQLException ex){
			return false;
		}
	}

}
