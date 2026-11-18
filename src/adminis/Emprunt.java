package adminis;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import com.mysql.jdbc.Statement;

import conn.Connec;

import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import java.sql.*;


public class Emprunt extends JFrame implements ActionListener {
	java.sql.Statement st;
	ResultSet rst;
	Connec cn=new Connec();
	JComboBox jcb,jcb2;
	JLabel lb1,lb2,lb3,lb4,lb5,lb6,lb7;
	JTextField tf1,tf5,tf6,tf7;
	JButton jb,jb2,bt3,blivre,butilisateur;
	public Emprunt(){
		this.setTitle("GesBiblio | Emprunts et retours");
		this.setSize(500,560);
		this.setLocationRelativeTo(null);
		this.setResizable(false);
		JPanel pn=new JPanel();
		pn.setLayout(null);
		pn.setBackground(new Color(0,200,200));
		add(pn);
		
		//label
		lb3=new JLabel("Enregistrer un emprunt");
		lb3.setBounds(120,45,280,28);
		//lb3.setForeground(Color.blue);
		lb3.setFont(new Font("Arial",Font.BOLD,18));
		pn.add(lb3);
				
		
		lb1=new JLabel("N° adhérent");
		lb1.setBounds(30,100,130,28);
		pn.add(lb1);
		 tf1=new JTextField();
		 tf1.setBounds(170,100,270,28);
		pn.add(tf1);
		
		lb2=new JLabel("Livre à emprunter");
		lb2.setBounds(30,145,130,28);
		pn.add(lb2);
		
		
		jcb=new JComboBox();
		jcb.setBounds(170,145,270,28);
		//ajout des titres des livres au combo livres
		String kk4="select titre from livre where disponible='OUI'";
try{
	st=cn.connecion().createStatement();
	rst=st.executeQuery(kk4);
	while(rst.next()){
	jcb.addItem(rst.getString("titre"));

	}
}
catch(SQLException ex){
	
}
		pn.add(jcb);
		/*combonom2 =new JComboBox();
		combonom2.setBounds(150,110,120,25);
		
		pn.add(combonom2);*/
		//button
		jb=new JButton("Enregistrer l'emprunt");
		jb.setBounds(165,188,170,30);
		jb.addActionListener(this);
		pn.add(jb);
		//retour
		//
		lb4=new JLabel("Enregistrer un retour");
		lb4.setBounds(120,240,280,28);
		//lb4.setForeground(Color.blue);
		lb4.setFont(new Font("Arial",Font.BOLD,18));
		pn.add(lb4);
		
		lb5=new JLabel("N° adhérent");
		lb5.setBounds(30,290,130,28);
		pn.add(lb5);
		 tf5=new JTextField();
		 tf5.setBounds(170,290,270,28);
		pn.add(tf5);
		//livre
		lb6=new JLabel("Livre à retourner");
		lb6.setBounds(30,335,130,28);
		pn.add(lb6);
		//combo
		jcb2=new JComboBox();
		jcb2.setBounds(170,335,270,28);
		//ajout des titres des livres au combo livres
		String kk5="select titre from livre where disponible='NON'";
try{
	st=cn.connecion().createStatement();
	rst=st.executeQuery(kk5);
	while(rst.next()){
	jcb2.addItem(rst.getString("titre"));

	}
}
catch(SQLException ex){
	
}
		pn.add(jcb2);
		//button
				jb2=new JButton("Enregistrer le retour");
				jb2.setBounds(165,378,170,30);
				jb2.addActionListener(this);
				pn.add(jb2);
		//bouton requete
				bt3=new JButton("Suivi");
				bt3.setBounds(20,475,130,32);
				bt3.addActionListener(this);
				pn.add(bt3);
		//bouton livre
				blivre=new JButton("Catalogue");
				blivre.setBounds(180,475,130,32);
				blivre.addActionListener(this);
				pn.add(blivre);
	 //bouton utlisateur
				butilisateur=new JButton("Adhérents");
				butilisateur.setBounds(340,475,130,32);
				butilisateur.addActionListener(this);
				pn.add(butilisateur);
		tf1.setToolTipText("Saisissez le numéro de carte de l'adhérent.");
		tf5.setToolTipText("Saisissez le numéro de carte de l'adhérent qui rend le livre.");
		jcb.setToolTipText("Choisissez le titre prêté.");
		jcb2.setToolTipText("Choisissez le titre rendu.");
		LegacyUi.appliquer(this, pn, "Emprunts et retours");
				
	}

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		Emprunt ep=new Emprunt();
		ep.setVisible(true);

	}

	@Override
	public void actionPerformed(ActionEvent e) {
		// TODO Auto-generated method stub
		if(e.getSource()==jb){
			String a,b;
			a=tf1.getText().trim();
			if (a.isEmpty() || jcb.getSelectedItem() == null) {
				JOptionPane.showMessageDialog(this,"Saisissez le numéro de carte et choisissez un livre.","Informations manquantes",JOptionPane.WARNING_MESSAGE);
				return;
			}
			b=jcb.getSelectedItem().toString();
			String qr="update livre set disponible='NON',idutilisateur=? where titre=? and disponible='OUI'";
			try{
				PreparedStatement ps=cn.connecion().prepareStatement(qr);
				ps.setString(1,a);
				ps.setString(2,b);
				if(JOptionPane.showConfirmDialog(this,"Confirmer l'emprunt de « "+b+" » pour l'adhérent n° "+a+" ?","Confirmer l'emprunt",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION){
					if (ps.executeUpdate() > 0) {
						JOptionPane.showMessageDialog(this,"Emprunt enregistré.");
						jcb.removeItem(b);
						jcb2.addItem(b);
					} else {
						JOptionPane.showMessageDialog(this,"Aucun livre n'a été mis à jour. Vérifiez le titre et l'adhérent.","Emprunt non enregistré",JOptionPane.WARNING_MESSAGE);
					}
				}
				
			}
			catch(SQLException ex){
				JOptionPane.showMessageDialog(this,"L'emprunt n'a pas pu être enregistré.\n"+ex.getMessage(),"Erreur",JOptionPane.ERROR_MESSAGE);
			}
		}
		if(e.getSource()==jb2){
			String a,b;
			a=tf5.getText().trim();
			if (a.isEmpty() || jcb2.getSelectedItem() == null) {
				JOptionPane.showMessageDialog(this,"Saisissez le numéro de carte et choisissez un livre.","Informations manquantes",JOptionPane.WARNING_MESSAGE);
				return;
			}
			b=jcb2.getSelectedItem().toString();
			String qr="update livre set disponible='OUI',idutilisateur=NULL where idutilisateur=? and titre=?";
			try{
				PreparedStatement ps=cn.connecion().prepareStatement(qr);
				ps.setString(1,a);
				ps.setString(2,b);
				if(JOptionPane.showConfirmDialog(this,"Confirmer le retour de « "+b+" » pour l'adhérent n° "+a+" ?","Confirmer le retour",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION){
					if (ps.executeUpdate() > 0) {
						JOptionPane.showMessageDialog(this,"Retour enregistré.");
						jcb2.removeItem(b);
						jcb.addItem(b);
					} else {
						JOptionPane.showMessageDialog(this,"Aucun emprunt correspondant n'a été trouvé.","Retour non enregistré",JOptionPane.WARNING_MESSAGE);
					}
				}
				
			}
			catch(SQLException ex){
				JOptionPane.showMessageDialog(this,"Le retour n'a pas pu être enregistré.\n"+ex.getMessage(),"Erreur",JOptionPane.ERROR_MESSAGE);
			}
			
		}
		//requetes
		if(e.getSource()==bt3){
			dispose();
			Requete rq=new Requete();
			rq.setVisible(true);
			
		}
		//livres
		if(e.getSource()==blivre){
			dispose();
			Livre lv=new Livre();
			lv.setVisible(true);
			
		}
		//utilisateur
		if(e.getSource()==butilisateur){
			dispose();
			Utilisateur ab=new Utilisateur();
			ab.setVisible(true);
			
		}
		//
		
	}

}