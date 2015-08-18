/*
Boring awt dropbox
Copyright (C) 2015  Mellon Green

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import javax.swing.*;

public class GenericDropbox extends JDialog {

    protected JLabel ambiguousMsg;
    protected JComboBox comboBox;
    protected JButton ok;
    public String val;
    public GenericDropbox(String titlename,String[] selections)
    {
        //super(EyelabClient.frame, titlename, true);
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints cs = new GridBagConstraints();
        val ="";
        cs.fill = GridBagConstraints.NONE;

        ambiguousMsg = new JLabel("Serveral serial ports detected, specify");
        cs.gridx = 0;
        cs.gridy = 0;
        cs.gridwidth = 2;
        panel.add(ambiguousMsg, cs);

        comboBox = new JComboBox(selections);
        cs.fill = GridBagConstraints.HORIZONTAL;
        cs.gridx = 0;
        cs.gridy = 1;
        cs.gridwidth = 2;
        panel.add(comboBox, cs);

        ok = new JButton("Ok");
        cs.fill = GridBagConstraints.NONE;
        cs.gridx = 1;
        cs.gridy = 2;
        cs.gridwidth = 1;
        panel.add(ok, cs);

        ok.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                val = (String)comboBox.getSelectedItem();
                dispose();
            }
        });

        ok.addKeyListener(new KeyAdapter()
        {
            public void keyPressed(KeyEvent e)
            {
                if(e.getKeyCode() == KeyEvent.VK_ENTER)
                {
                    ok.doClick();
                }
            }
        });

        getContentPane().add(panel, BorderLayout.CENTER);
        pack();
        setResizable(false);
       // setLocationRelativeTo(EyelabClient.frame);
    }
}
