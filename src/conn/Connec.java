package conn;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Connec {
	Connection con;

	public Connec(){
		try{
			Class.forName("com.mysql.jdbc.Driver");
			//Class.forName("sun.jdbc.odbc.JdbcOdbcDriver");
			con=DriverManager.getConnection("jdbc:mysql://localhost:3306/biblio?useSSL=false","root","passer");
			//String database ="jdbc:odbc:Driver={Microsoft Access Driver (*.mdb)};DBQ=gestionStock.mdb;";
			//Connection con = DriverManager.getConnection(database, "", "");
			System.out.println("connected!!");
		} catch (ClassNotFoundException e) {
			throw new IllegalStateException("MySQL JDBC driver was not found.", e);
		} catch (SQLException e) {
			throw new IllegalStateException(
					"Could not connect to MySQL database 'biblio' on localhost. Check that MySQL is running, "
							+ "the database exists, and the username/password in Connec.java are correct.",
					e);
		}
	}
	public Connection connecion(){
		return con;
	}
}