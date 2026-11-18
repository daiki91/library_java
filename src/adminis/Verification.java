package adminis;
import java.sql.*;

import conn.Connec;

public class Verification {
	Connec cn=new Connec();
	
	public boolean verifier(String titre) throws SQLException {
		String qr="select 1 from livre where titre=? and disponible='OUI'";
		try (PreparedStatement ps=cn.connecion().prepareStatement(qr)) {
			ps.setString(1,titre);
			try (ResultSet rst=ps.executeQuery()) {
				return rst.next();
			}
		}
	}

}
