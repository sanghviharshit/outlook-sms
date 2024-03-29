package mysms.util.outlook.gui;

import java.awt.*;
import java.awt.event.*;

import java.lang.reflect.InvocationTargetException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.*;
import javax.swing.event.*;

import com.moyosoft.connector.com.*;
import com.moyosoft.connector.exception.*;
import com.moyosoft.connector.ms.outlook.*;
import com.moyosoft.connector.ms.outlook.contact.*;
import com.moyosoft.connector.ms.outlook.folder.*;
import com.moyosoft.connector.ms.outlook.item.*;
import java.util.Comparator;
import java.util.Vector;
import javax.mail.Session;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import mysms.DAO.OptionsDAO;
import mysms.MySmsMain;
import mysms.beans.ContactBean;
import mysms.util.ToolTip;
import mysms.util.outlook.folderchooser.*;
import mysms.util.outlook.gui.contact.*;
import mysms.util.outlook.gui.contact.event.*;
import mysms.util.outlook.gui.model.*;

public class ContactExplorer extends JFrame
{
    private JLabel mLabelFolder = new JLabel("Folder");
    private JTextField mFieldFolder = new JTextField();
    private JButton mButtonChooseFolder = new JButton("Choose");

    private JButton mButtonAdd = new JButton("Add new");
    private JButton mButtonEdit = new JButton("Edit");
    private JButton mButtonRemove = new JButton("Remove");
    private JButton mButtonOpen = new JButton("Open");

    private JButton mButtonSelect = new JButton("Select");

    private JTable mTable = null;
    private ContactsTableModel mTableModel = null;
    private OutlookFolder mSelectedFolder = null;

    private Outlook outlookApplication = null;

    private String phoneNum;

    private ContactDialogListener mDialogListener = new ContactDialogListener()
    {
        public void closePressed(ContactDialogEvent pEvent)
        {
            pEvent.getContactDialog().close();
        }

        public void saveAndClosePressed(ContactDialogEvent pEvent)
        {
            OutlookContact contact = pEvent.getContact();
            if(contact == null)
            {
                addNewContact(pEvent.getContactDialog());
            }
            else
            {
                saveContact(pEvent.getContactDialog(), contact);
            }
        }

        public void deletePressed(ContactDialogEvent pEvent)
        {
            deleteContact(pEvent.getContactDialog(), pEvent.getContact());
        }
    };

    public ContactExplorer()
    {
        super("Outlook Contacts Explorer");
        init();
    }

    public ContactExplorer(String pTitle)
    {
        super(pTitle);
        init();
    }

    protected void init()
    {
        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        addWindowListener(new java.awt.event.WindowAdapter()
        {
            public void windowClosing(java.awt.event.WindowEvent e)
            {
                exitApplication();
            };

            public void windowOpened(WindowEvent e)
            {
                initApplication();
            }
        });

        JPanel folderChoicePanel = createFolderChoicePanel();
        JPanel buttonsPanel = createButtonsPanel();
        JPanel tablePanel = createTablePanel();

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        mainPanel.add(folderChoicePanel, BorderLayout.NORTH);
        mainPanel.add(tablePanel, BorderLayout.CENTER);
        mainPanel.add(buttonsPanel, BorderLayout.SOUTH);

        this.getContentPane().setLayout(new BorderLayout());
        this.getContentPane().add(mainPanel, BorderLayout.CENTER);

        setSize(620, 420);
        centerOnScreen(this);
    }
    
    private static void centerOnScreen(Window window)
    {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        Dimension windowSize = window.getSize();
        windowSize.height = Math.min(windowSize.height + 20, screenSize.height);
        windowSize.width = Math.min(windowSize.width, screenSize.width);
        window.setLocation((screenSize.width - windowSize.width) / 2,
                (screenSize.height - windowSize.height) / 2);
    }

    private static void setWaitCursorSafe(Container pContainer)
    {
        try
        {
            pContainer.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        }
        catch(Throwable t)
        {
        }
    }

    private static void setDefaultCursorSafe(Container pContainer)
    {
        try
        {
            pContainer.setCursor(Cursor.getDefaultCursor());
        }
        catch(Throwable t)
        {
        }
    }

    protected JPanel createFolderChoicePanel()
    {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        mFieldFolder.setEditable(false);
        mFieldFolder.setPreferredSize(new Dimension(200, (int) mFieldFolder
                .getPreferredSize().getHeight()));

        panel.add(mLabelFolder);
        panel.add(mFieldFolder);
        panel.add(mButtonChooseFolder);

        mButtonChooseFolder.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                chooseFolder();
            }
        });

        return panel;
    }

    protected JPanel createButtonsPanel()
    {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        mButtonEdit.setEnabled(false);
        mButtonRemove.setEnabled(false);
        mButtonOpen.setEnabled(false);

        panel.add(mButtonSelect);
        panel.add(mButtonAdd);
        panel.add(mButtonEdit);
        panel.add(mButtonRemove);
        panel.add(mButtonOpen);

        mButtonSelect.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                selectContact();
            }
        });

        mButtonEdit.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                editContact();
            }
        });
        mButtonAdd.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                addContact();
            }
        });
        mButtonRemove.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                removeContact();
            }
        });
        mButtonOpen.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                openContact();
            }
        });

        return panel;
    }

    protected JPanel createTablePanel()
    {
        JPanel panel = new JPanel(new BorderLayout());

        mTableModel = new ContactsTableModel();
        mTable = new JTable(mTableModel);

        JScrollPane scrollPane = new JScrollPane(mTable,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getViewport().setBackground(Color.white);

        panel.add(scrollPane, BorderLayout.CENTER);

        mTable.getSelectionModel().addListSelectionListener(
                new ListSelectionListener()
                {
                    public void valueChanged(ListSelectionEvent arg0)
                    {
                        tableSelectionChanged();
                    }
                });

        mTable.addMouseListener(new MouseAdapter()
                {
                @Override
                public void mouseClicked(MouseEvent me)
                {
                    if(me.getClickCount() == 2)
                    {
                        selectContact();
                    }
                }
                });
//        mTable.setUpdateSelectionOnSort(true);
//        mTable.setCellSelectionEnabled(true);
        TableRowSorter sorter = new TableRowSorter(mTableModel);
        mTable.setRowSorter(sorter);

        return panel;
    }

    protected OutlookItemID getSelectedContactItemId()
    {
        int row = mTable.getSelectedRow();
        if(row >= 0 && row < mTableModel.getRowCount())
        {
            OutlookItemID id = mTableModel.getContactItemIdAtRow(row);
            return id;
        }
        return null;
    }

    protected void tableSelectionChanged()
    {
                SwingUtilities.invokeLater(new Runnable()
                {
                public void run()
                    {
                    boolean isContactSelected = getSelectedContactItemId() != null;
                    mButtonEdit.setEnabled(isContactSelected);
                    mButtonRemove.setEnabled(isContactSelected);
                    mButtonOpen.setEnabled(isContactSelected);
                    mButtonSelect.setEnabled(isContactSelected);
                    }
                });
    }

    private void chooseFolder()
    {
        try
        {
            setWaitCursorSafe(getContentPane());
            FoldersCollection folders = null;
            try
            {
                folders = outlookApplication.getFolders();
            }
            finally
            {
                setDefaultCursorSafe(getContentPane());
            }

            OutlookFolder folder = FolderChooser.open(this, folders);
            if(folder != null)
            {
                setFolder(folder);
            }
        }
        catch(ComponentObjectModelException e)
        {
            ComErrorDialog.open(this, e);
        }
    }

    private void editContact()
    {
        try
        {
            OutlookItemID id = getSelectedContactItemId();
            if(id != null)
            {
                ContactDialog.open(this, mDialogListener,
                        (OutlookContact) mSelectedFolder.getItems()
                                .getItemById(id));
            }
        }
        catch(ComponentObjectModelException e)
        {
            ComErrorDialog.open(this, e);
        }

    }

    private void selectContact()
    {
        
            OutlookItemID id = getSelectedContactItemId();
//            ContactBean
            ToolTip.closeErrorMessage();
            
            if(id != null)
            {
                ListContact contact = new ListContact((OutlookContact) mSelectedFolder.getItems().getItemById(id));
                
                phoneNum = contact.getPrimaryPhone();
                if(phoneNum.equals(""))
                {
//                    System.out.println("No Primary Phone");
                    phoneNum = contact.getMobilePhone();
                    if(phoneNum.equals(""))
                    {
//                        System.out.println("No Mobile Phone");
                        phoneNum = contact.getOtherPhone();
                        if(phoneNum.equals(""))
                        {
//                            System.out.println("No Other Phone");
                            ToolTip.showTableCellErrorMessage(mTable,
                                    "No Number found Associated with this contact",
                                    mTable.getSelectedRow(),
                                    mTable.getSelectedColumn());
                            return;
                        }
                    }
                }
//                System.out.println("Phone : " + phoneNum);
//                ContactBean cBean = new ContactBean();
//                cBean.setNumber(phoneNum);
                MySmsMain.fromcontactFrame(phoneNum);

//                System.out.print(phoneNum);
            }
    }


    private void addContact()
    {
        ContactDialog.open(this, mDialogListener);
    }

    private void removeContact()
    {
        if(MessageDialog.openYesNoCancel(this, "Are you sure ?") == MessageDialog.BUTTON_YES)
        {
            setWaitCursorSafe(getContentPane());
            try
            {
                OutlookItemID id = getSelectedContactItemId();
                if(id != null)
                {
                    mSelectedFolder.getItems().deleteItem(id);
                    mTableModel.removeContactById(id);
                }
            }
            catch(ComponentObjectModelException ex)
            {
                ComErrorDialog.open(this, ex);
            }
            finally
            {
                setDefaultCursorSafe(getContentPane());
            }
        }
    }

    private void convertContact(ContactDialog pDialog, OutlookContact pContact)
    {


        setWaitCursorSafe(pDialog);
        try
        {
            if(pContact.getMobileTelephoneNumber() != null)
                pContact.setMobileTelephoneNumber(pContact.getOtherTelephoneNumber());
            pDialog.storeTo(pContact);
            
            pContact.save();
            mTableModel.updateContact(pContact);
            pDialog.close();
        }
        catch(ComponentObjectModelException ex)
        {
            ComErrorDialog.open(pDialog, ex);
        }
        finally
        {
            setDefaultCursorSafe(pDialog);
        }
    }


    private void saveContact(ContactDialog pDialog, OutlookContact pContact)
    {
        setWaitCursorSafe(pDialog);
        try
        {
            if(pDialog.hasChanged(pContact))
            {
                pDialog.storeTo(pContact);
            }
            pContact.save();
            mTableModel.updateContact(pContact);
            pDialog.close();
        }
        catch(ComponentObjectModelException ex)
        {
            ComErrorDialog.open(pDialog, ex);
        }
        finally
        {
            setDefaultCursorSafe(pDialog);
        }
    }

    private void deleteContact(ContactDialog pDialog, OutlookContact pContact)
    {
        if(MessageDialog.openYesNoCancel(pDialog, "Are you sure ?") == MessageDialog.BUTTON_YES)
        {
            setWaitCursorSafe(pDialog);
            try
            {
                OutlookItemID id = pContact.getItemId();
                mSelectedFolder.getItems().deleteItem(id);
                mTableModel.removeContactById(id);
                pDialog.close();
            }
            catch(ComponentObjectModelException ex)
            {
                ComErrorDialog.open(pDialog, ex);
            }
            finally
            {
                setDefaultCursorSafe(pDialog);
            }
        }
    }

    private void addNewContact(ContactDialog pDialog)
    {
        setWaitCursorSafe(pDialog);
        try
        {
            OutlookItem item = mSelectedFolder.getItems().createNew(
                    ItemType.CONTACT);
            if(item == null)
            {
                MessageDialog.openOk(pDialog,
                        "Error occured when creating outlook item.");
            }
            else
            {
                OutlookContact contact = (OutlookContact) item;
                pDialog.storeTo(contact);

                contact.save();
                mTableModel.addNewContact(contact);
                pDialog.close();
            }
        }
        catch(ComponentObjectModelException ex)
        {
            ComErrorDialog.open(pDialog, ex);
        }
        finally
        {
            setDefaultCursorSafe(pDialog);
        }
    }

    private void openContact()
    {
        try
        {
            OutlookItemID id = getSelectedContactItemId();
            if(id != null)
            {
                OutlookItem item = mSelectedFolder.getItems().getItemById(id);

                if(item == null || !item.getType().isContact())
                {
                    MessageDialog.openOk(this, "Cannot open this item.");
                }

                ((OutlookContact) item).display();
            }
        }
        catch(ComponentObjectModelException ex)
        {
            ComErrorDialog.open(this, ex);
        }
    }

    protected void setFolder(OutlookFolder pFolder)
    {
        mSelectedFolder = pFolder;
        updateFolderSelection();
    }

    private void updateFolderSelection()
    {
        if(mSelectedFolder == null)
        {
            mFieldFolder.setText("");
        }
        else
        {
            mFieldFolder.setText(mSelectedFolder.getName());
        }

         final SwingWorker worker = new SwingWorker() {
            @Override
            protected Object doInBackground() throws Exception {
                                threadStarted();

                setWaitCursorSafe(ContactExplorer.this.getContentPane());
                threadStarted();
                try
                {
                    mTableModel.loadContacts(mSelectedFolder);
                }
                catch(ComponentObjectModelException e)
                {
                    e.printStackTrace();
                    ComErrorDialog.open(ContactExplorer.this, e);
                }
                finally
                {
                    setDefaultCursorSafe(ContactExplorer.this.getContentPane());
                    threadFinished();
                    return true;
                }

            }
        };
        worker.execute();

    }

    protected void threadStarted()
    {
            SwingUtilities.invokeLater(new Runnable() {

                public void run() {
                    mButtonAdd.setEnabled(false);
                    mButtonChooseFolder.setEnabled(false);
                    mButtonEdit.setEnabled(false);
                    mButtonRemove.setEnabled(false);
                    mButtonOpen.setEnabled(false);
                    mButtonSelect.setEnabled(false);
                }
            });
    }

    protected void threadFinished()
    {

        SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    mButtonAdd.setEnabled(true);
                    mButtonChooseFolder.setEnabled(true);
                }
            });
//        tableSelectionChanged();

    }

    protected void initApplication()
    {
        try
        {
            outlookApplication = new Outlook();

            setFolder(outlookApplication.getDefaultFolder(FolderType.CONTACTS));
        }
        catch(LibraryNotFoundException e)
        {
            MessageDialog.openOk(this, "Unable to find the library.");
            e.printStackTrace();
            System.exit(0);
        }
        catch(ComponentObjectModelException e)
        {
            ComErrorDialog.open(this, e);
        }
    }

    private void exitApplication()
    {
        if(outlookApplication != null)
        {
            outlookApplication.dispose();
            outlookApplication = null;
        }

        dispose();
        //System.exit(0);
    }

    public static void open()
    {
        ContactExplorer frame = new ContactExplorer();
        frame.setVisible(true);
    }

    public static void main(String[] args)
    {
        // Set the look and feel
        try
        {
            String systemLaf = UIManager.getSystemLookAndFeelClassName();
            UIManager.setLookAndFeel(systemLaf);

            UIManager.put("Table.focusCellHighlightBorder", BorderFactory
                    .createEmptyBorder(1, 1, 1, 1));
        }
        catch(Exception e)
        {
        }
        ContactExplorer.open();
    }
}
