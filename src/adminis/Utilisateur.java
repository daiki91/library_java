package adminis;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

import conn.Connec;

import java.sql.*;

public class Utilisateur extends JFrame implements ActionListener {
	JLabel lb1,lb2,lb3,lb4,lb5,lbtitre;
	JTextField tf1,tf2,tf3,tf4,tf5;
	JButton bt1,bt2,bt3,bt4,bt5,bt6,blivre,bpret;
	ResultSet rst;
	Statement st;
	Connec cn=new Connec();
	JTable jt;
	JScrollPane js;
	
	public Utilisateur(){
		this.setTitle("GesBiblio | Adhérents");
		this.setSize(700,400);
		this.setLocationRelativeTo(null);
		this.setResizable(false);
		JPanel pn=new JPanel();
		pn.setLayout(null);
		pn.setBackground(new Color(0,200,200));
		add(pn);
		//titre
		lbtitre=new JLabel("Gestion des adhérents");
		lbtitre.setBounds(10,10,300,30);
		lbtitre.setFont(new Font("Arial",Font.BOLD,16));
		pn.add(lbtitre);
		
		//label
		lb1=new JLabel("N° adhérent");
		lb1.setBounds(30,50,100,30);
		pn.add(lb1);
		tf1=new JTextField();
		tf1.setBounds(130,50,100,30);
		pn.add(tf1);
		//
		lb2=new JLabel("Nom complet");
		lb2.setBounds(30,90,100,30);
		pn.add(lb2);
		
		tf2=new JTextField();
		tf2.setBounds(130,90,150,30);
		pn.add(tf2);
		//
		
		
		
		pn.add(tf2);
		//button
		bt1=new JButton("Ajouter");
		bt1.setBounds(50,130,90,30);
		bt1.addActionListener(this);
		pn.add(bt1);
		
		bt2=new JButton("Supprimer");
		bt2.setBounds(50,170,110,30);
		bt2.addActionListener(this);
		pn.add(bt2);
		
		bt4=new JButton("Rechercher");
		bt4.setBounds(230,50,80,30);
		bt4.addActionListener(this);
		pn.add(bt4);
		
		bt3=new JButton("Suivi");
		bt3.setBounds(50,240,110,30);
		bt3.addActionListener(this);
		pn.add(bt3);
		
		bt5=new JButton("Rafraîchir");
		bt5.setBounds(50,280,110,30);
		bt5.addActionListener(this);
		pn.add(bt5);
		
		bt6=new JButton("Modifier");
		bt6.setBounds(50,205,110,30);
		bt6.addActionListener(this);
		pn.add(bt6);
		
		blivre=new JButton("Catalogue");
		blivre.setBounds(180,280,110,30);
		blivre.addActionListener(this);
		pn.add(blivre);
		
		bpret=new JButton("Prêts");
		bpret.setBounds(180,240,110,30);
		bpret.addActionListener(this);
		pn.add(bpret);
		
		DefaultTableModel df=new DefaultTableModel();
		init();
		df.addColumn("IDENTIFIANT");
		df.addColumn("NOM ET PRENOM");
		jt.setModel(df);
		pn.add(js);
		
		String rq="select * from utilisateur";
		try{
			st=cn.connecion().createStatement();
			rst=st.executeQuery(rq);
			while(rst.next()){
				df.addRow(new Object[]{
						rst.getString("idutilisateur"),rst.getString("nomUtilisateur")
				});
				
			}
		}
		catch(SQLException ex){
			
		}
		tf1.setToolTipText("Saisissez le numéro unique de carte de l'adhérent.");
		tf2.setToolTipText("Saisissez le nom et le prénom de l'adhérent.");
		LegacyUi.appliquer(this, pn, "Gestion des adhérents");
	}
	public void init(){
		jt=new JTable();
		js=new JScrollPane();
		js.setViewportView(jt);
		js.setBounds(320,10,350,300);
	}

	public static void main(String[] args) {
		// TODO Auto-generated method stub
    Utilisateur ab=new Utilisateur();
    ab.setVisible(true);
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		// TODO Auto-generated method stub
		//ajout
		if(e.getSource()==bt1){
		String a,b;
		a=tf1.getText();b=tf2.getText();
		String qr="insert into utilisateur values(?,?)";
		try{
			PreparedStatement ps=cn.connecion().prepareStatement(qr);
			ps.setString(1,a);
			ps.setString(2,b);
			if(JOptionPane.showConfirmDialog(this,"Ajouter « "+b+" » à la liste des adhérents ?","Confirmer l'ajout",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION){
				ps.executeUpdate();
				JOptionPane.showMessageDialog(this,"L'adhérent a été ajouté.");
			}
		}
		catch(SQLException ex){
			JOptionPane.showMessageDialog(this,"Impossible d'ajouter l'adhérent.\n"+ex.getMessage(),"Erreur",JOptionPane.ERROR_MESSAGE);
		}
			
		}
		if(e.getSource()==bt2){
			String a;
			a=tf1.getText();
			String qr="delete from utilisateur where idutilisateur=?";
			try{
				PreparedStatement ps=cn.connecion().prepareStatement(qr);
				ps.setString(1,a);
				if(JOptionPane.showConfirmDialog(this,"Supprimer l'adhérent n° "+a+" ?","Confirmer la suppression",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION){
					ps.executeUpdate();
					JOptionPane.showMessageDialog(this,"La demande de suppression a été traitée.");
				}
			}
			catch(SQLException ex){
				JOptionPane.showMessageDialog(this,"Impossible de supprimer cet adhérent. Il a peut-être encore des emprunts associés.\n"+ex.getMessage(),"Erreur",JOptionPane.ERROR_MESSAGE);
			}
		}
		//rech
      if(e.getSource()==bt4){
    	  String a;
			a=tf1.getText();
			String qr="select * from utilisateur where idutilisateur=?";
			try{
				PreparedStatement ps=cn.connecion().prepareStatement(qr);
				ps.setString(1,a);
				rst=ps.executeQuery();
				if(rst.next()){
					tf2.setText(rst.getString("nomUtilisateur"));
				}
				else
					JOptionPane.showMessageDialog(this,"Aucun adhérent ne correspond à ce numéro.","Adhérent introuvable",JOptionPane.INFORMATION_MESSAGE);
			}
			catch(SQLException ex){
				JOptionPane.showMessageDialog(this,"La recherche a échoué.\n"+ex.getMessage(),"Erreur",JOptionPane.ERROR_MESSAGE);
			}
    	  
      }
		if(e.getSource()==bt3){
			dispose();
			Requete rq=new Requete();
			rq.setVisible(true);
			
		}
		if(e.getSource()==bt5){
			dispose();
			Utilisateur ab=new Utilisateur();
			ab.setVisible(true);
			
			
		}
		//modification
		if(e.getSource()==bt6){
			String a,b;
			a=tf1.getText();b=tf2.getText();
			String qr="update utilisateur set nomUtilisateur=? where idutilisateur=?";
			try{
				PreparedStatement ps=cn.connecion().prepareStatement(qr);
				ps.setString(1,b);
				ps.setString(2,a);
				if(JOptionPane.showConfirmDialog(this,"Enregistrer les modifications de "+b+" ?","Confirmer la modification",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION){
					ps.executeUpdate();
					JOptionPane.showMessageDialog(this,"Les informations de l'adhérent ont été modifiées.");
				}
			}
			catch(SQLException ex){
				JOptionPane.showMessageDialog(this,"Impossible de modifier cet adhérent.\n"+ex.getMessage(),"Erreur",JOptionPane.ERROR_MESSAGE);
			}
				
			}
		//aller vers l'interface graphique de gestion des livres
		if(e.getSource()==blivre){
				dispose();
				Livre lv=new Livre();
				lv.setVisible(true);	
			}
		//emprunt
		if(e.getSource()==bpret){
			dispose();
			Emprunt ep=new Emprunt();
			ep.setVisible(true);	
		}
	}

}
