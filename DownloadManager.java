import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Observable;
import java.util.Observer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;

public class DownloadManager extends JFrame implements Observer {
    private JTextField nameTextField = new JTextField(15);
    private JTextField sizeTextField = new JTextField(12);
    private JTextField speedTextField = new JTextField(12);

    private DownloadsTableModel tableModel = new DownloadsTableModel();

    private JTable table;

    private JButton pauseButton = new JButton("Pause");

    private JButton resumeButton = new JButton("Resume");

    private JButton cancelButton, clearButton;

    private Download selectedDownload;

    private boolean clearing;

    public DownloadManager() {
        setTitle("File Download Manager Simulator");
        setSize(750, 480);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Set up add panel.
        JPanel addPanel = new JPanel();

        nameTextField.setText("Enter the file name");
        sizeTextField.setText("Enter the file size (MB)");
        speedTextField.setText("Enter the internet speed");

        addPanel.setBackground(Color.DARK_GRAY);
        addPanel.add(nameTextField);
        addPanel.add(sizeTextField);
        addPanel.add(speedTextField);
        JButton addButton = new JButton("Add Download");
        addButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                actionAdd();
            }
        });
        addPanel.add(addButton);

        // Set up Downloads table.
        table = new JTable(tableModel);
        table.setBackground(Color.lightGray);
        table.setSelectionBackground(Color.BLACK);
        table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                tableSelectionChanged();
            }
        });
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        ProgressRenderer renderer = new ProgressRenderer(0, 100);
        renderer.setStringPainted(true); // show progress text
        table.setDefaultRenderer(JProgressBar.class, renderer);

        table.setRowHeight((int) renderer.getPreferredSize().getHeight());

        JPanel downloadsPanel = new JPanel();
        downloadsPanel.setBackground(Color.LIGHT_GRAY);
        downloadsPanel.setBorder(BorderFactory.createTitledBorder("Downloads"));
        downloadsPanel.setLayout(new BorderLayout());
        downloadsPanel.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel buttonsPanel = new JPanel();
        buttonsPanel.setBackground(Color.DARK_GRAY);

        pauseButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                actionPause();
            }
        });
        pauseButton.setEnabled(false);
        buttonsPanel.add(pauseButton);

        resumeButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                actionResume();
            }
        });
        resumeButton.setEnabled(false);
        buttonsPanel.add(resumeButton);

        cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                actionCancel();
            }
        });
        cancelButton.setEnabled(false);
        buttonsPanel.add(cancelButton);

        clearButton = new JButton("Clear");
        clearButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                actionClear();
            }
        });
        clearButton.setEnabled(false);
        buttonsPanel.add(clearButton);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(addPanel, BorderLayout.NORTH);
        getContentPane().add(downloadsPanel, BorderLayout.CENTER);
        getContentPane().add(buttonsPanel, BorderLayout.SOUTH);
    }

    private void actionAdd() {

        String name = nameTextField.getText();
        float size = Float.parseFloat(sizeTextField.getText());
        float speed = Float.parseFloat(speedTextField.getText());

        tableModel.addDownload(new Download(name, size, speed));
        nameTextField.setText(""); // reset add text field
        sizeTextField.setText("");
        speedTextField.setEditable(false);
    }

    private void tableSelectionChanged() {
        if (selectedDownload != null)
            selectedDownload.deleteObserver(DownloadManager.this);

        if (!clearing && table.getSelectedRow() > -1) {
            selectedDownload = tableModel.getDownload(table.getSelectedRow());
            selectedDownload.addObserver(DownloadManager.this);
            updateButtons();
        }
    }

    private void actionPause() {
        selectedDownload.pause();
        updateButtons();
    }

    private void actionResume() {
        selectedDownload.resume();
        updateButtons();
    }

    private void actionCancel() {
        selectedDownload.cancel();
        updateButtons();
    }

    private void actionClear() {
        clearing = true;
        tableModel.clearDownload(table.getSelectedRow());
        clearing = false;
        selectedDownload = null;
        updateButtons();
    }

    private void updateButtons() {
        if (selectedDownload != null) {
            int status = selectedDownload.getStatus();
            switch (status) {
                case Download.DOWNLOADING:
                    pauseButton.setEnabled(true);
                    resumeButton.setEnabled(false);
                    cancelButton.setEnabled(true);
                    clearButton.setEnabled(false);
                    break;
                case Download.PAUSED:
                    pauseButton.setEnabled(false);
                    resumeButton.setEnabled(true);
                    cancelButton.setEnabled(true);
                    clearButton.setEnabled(false);
                    break;
                default: // COMPLETE or CANCELLED
                    pauseButton.setEnabled(false);
                    resumeButton.setEnabled(false);
                    cancelButton.setEnabled(false);
                    clearButton.setEnabled(true);
            }
        } else {
            pauseButton.setEnabled(false);
            resumeButton.setEnabled(false);
            cancelButton.setEnabled(false);
            clearButton.setEnabled(false);
        }
    }

    public void update(Observable o, Object arg) {
        // Update buttons if the selected download has changed.
        if (selectedDownload != null && selectedDownload.equals(o))
            updateButtons();
    }

    // Run the Download Manager.
    public static void main(String[] args) {
        DownloadManager manager = new DownloadManager();
        manager.setVisible(true);
    }
}

class Download extends Observable implements Runnable {

    public static final String STATUSES[] = { "Downloading", "Paused", "Complete", "Cancelled"};

    public String name;

    public static final int DOWNLOADING = 0;

    public static final int PAUSED = 1;

    public static final int COMPLETE = 2;

    public static final int CANCELLED = 3;

    private float size; // size of download in bytes

    private float speed;

    private float downloaded; // number of bytes downloaded

    private int status; // current status of download

    // Constructor for Download.
    public Download(String name, Float size, Float speed) {
        this.name = name;
        this.size = size;
        this.speed = speed;
        downloaded = 0;
        status = DOWNLOADING;

        // Begin the download.
        download();
    }

    // Get this download's name.
    public String getName() {
        return name;
    }

    // Get this download's size.
    public float getSize() {
        return size;
    }

    // Get this download's progress.
    public float getProgress() {
        return ((float) downloaded / size) * 100;
    }

    public int getStatus() {
        return status;
    }

    public void pause() {
        status = PAUSED;
        stateChanged();
    }

    public void resume() {
        status = DOWNLOADING;
        stateChanged();
        download();
    }

    public void cancel() {
        status = CANCELLED;
        stateChanged();
    }

    private void download() {
        Thread thread = new Thread(this);
        thread.start();
    }

    // Simulate download file.
    public void run() {
        while(downloaded<= size) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                System.out.println(e);
            }
            if(status == DOWNLOADING)
            {
                downloaded += speed;
                stateChanged();
            }
        }

        if (status == DOWNLOADING) {
            status = COMPLETE;
            stateChanged();
        }
    }

    private void stateChanged() {
        setChanged();
        notifyObservers();
    }
}

class DownloadsTableModel extends AbstractTableModel implements Observer {
    private static final String[] columnNames = { "File Name", "Size", "Progress", "Status" };

    private static final Class[] columnClasses = { String.class, String.class, JProgressBar.class,
            String.class };

    private ArrayList<Download> downloadList = new ArrayList<Download>();

    public void addDownload(Download download) {
        download.addObserver(this);
        downloadList.add(download);
        fireTableRowsInserted(getRowCount() - 1, getRowCount() - 1);
    }

    public Download getDownload(int row) {
        return (Download) downloadList.get(row);
    }

    public void clearDownload(int row) {
        downloadList.remove(row);
        fireTableRowsDeleted(row, row);
    }

    public int getColumnCount() {
        return columnNames.length;
    }

    public String getColumnName(int col) {
        return columnNames[col];
    }

    public Class getColumnClass(int col) {
        return columnClasses[col];
    }

    public int getRowCount() {
        return downloadList.size();
    }

    public Object getValueAt(int row, int col) {
        Download download = downloadList.get(row);
        switch (col) {
            case 0: // Name
                return download.getName();
            case 1: // Size
                float size = download.getSize();
                return (size == -1) ? "" : Float.toString(size);
            case 2: // Progress
                return download.getProgress();
            case 3: // Status
                return Download.STATUSES[download.getStatus()];
        }
        return "";
    }

    public void update(Observable o, Object arg) {
        int index = downloadList.indexOf(o);
        fireTableRowsUpdated(index, index);
    }
}

class ProgressRenderer extends JProgressBar implements TableCellRenderer {
    public ProgressRenderer(int min, int max) {
        super(min, max);
    }

    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                   boolean hasFocus, int row, int column) {
        setValue((int) ((Float) value).floatValue());
        return this;
    }
}