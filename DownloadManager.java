import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Observable;
import java.util.Observer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
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
  private JTextField addTextField = new JTextField(30);

  private DownloadsTableModel tableModel = new DownloadsTableModel();

  private JTable table;

  private JButton pauseButton = new JButton("Pause");

  private JButton resumeButton = new JButton("Resume");

  private JButton cancelButton, clearButton;

  private Download selectedDownload;

  private boolean clearing;

  public DownloadManager() {
    setTitle("Download Manager");
    setSize(640, 480);
    addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent e) {
        System.exit(0);
      }
    });
    JMenuBar menuBar = new JMenuBar();
    JMenu fileMenu = new JMenu("File");
    fileMenu.setMnemonic(KeyEvent.VK_F);
    JMenuItem fileExitMenuItem = new JMenuItem("Exit", KeyEvent.VK_X);
    fileExitMenuItem.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        System.exit(0);
      }
    });
    fileMenu.add(fileExitMenuItem);
    menuBar.add(fileMenu);
    setJMenuBar(menuBar);

    // Set up add panel.
    JPanel addPanel = new JPanel();

    addPanel.add(addTextField);
    JButton addButton = new JButton("Add Download");
    addButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        actionAdd();
      }
    });
    addPanel.add(addButton);

    // Set up Downloads table.

    table = new JTable(tableModel);
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
    downloadsPanel.setBorder(BorderFactory.createTitledBorder("Downloads"));
    downloadsPanel.setLayout(new BorderLayout());
    downloadsPanel.add(new JScrollPane(table), BorderLayout.CENTER);

    JPanel buttonsPanel = new JPanel();

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
    URL verifiedUrl = verifyUrl(addTextField.getText());
    if (verifiedUrl != null) {
      tableModel.addDownload(new Download(verifiedUrl));
      addTextField.setText(""); // reset add text field
    } else {
      JOptionPane.showMessageDialog(this, "Invalid Download URL", "Error",
          JOptionPane.ERROR_MESSAGE);
    }
  }

  private URL verifyUrl(String url) {
    if (!url.toLowerCase().startsWith("http://"))
      return null;

    URL verifiedUrl = null;
    try {
      verifiedUrl = new URL(url);
    } catch (Exception e) {
      return null;
    }

    if (verifiedUrl.getFile().length() < 2)
      return null;

    return verifiedUrl;
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
      case Download.ERROR:
        pauseButton.setEnabled(false);
        resumeButton.setEnabled(true);
        cancelButton.setEnabled(false);
        clearButton.setEnabled(true);
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
  private static final int MAX_BUFFER_SIZE = 1024;

  public static final String STATUSES[] = { "Downloading", "Paused", "Complete", "Cancelled",
      "Error" };

  public static final int DOWNLOADING = 0;

  public static final int PAUSED = 1;

  public static final int COMPLETE = 2;

  public static final int CANCELLED = 3;

  public static final int ERROR = 4;

  private URL url; // download URL

  private int size; // size of download in bytes

  private int downloaded; // number of bytes downloaded

  private int status; // current status of download

  // Constructor for Download.
  public Download(URL url) {
    this.url = url;
    size = -1;
    downloaded = 0;
    status = DOWNLOADING;

    // Begin the download.
    download();
  }

  // Get this download's URL.
  public String getUrl() {
    return url.toString();
  }

  // Get this download's size.
  public int getSize() {
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

  private void error() {
    status = ERROR;
    stateChanged();
  }

  private void download() {
    Thread thread = new Thread(this);
    thread.start();
  }

  // Get file name portion of URL.
  private String getFileName(URL url) {
    String fileName = url.getFile();
    return fileName.substring(fileName.lastIndexOf('/') + 1);
  }

  // Download file.
  public void run() {
    RandomAccessFile file = null;
    InputStream stream = null;

    try {
      // Open connection to URL.
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();

      // Specify what portion of file to download.
      connection.setRequestProperty("Range", "bytes=" + downloaded + "-");

      // Connect to server.
      connection.connect();

      // Make sure response code is in the 200 range.
      if (connection.getResponseCode() / 100 != 2) {
        error();
      }

      // Check for valid content length.
      int contentLength = connection.getContentLength();
      if (contentLength < 1) {
        error();
      }

      /*
       * Set the size for this download if it hasn't been already set.
       */
      if (size == -1) {
        size = contentLength;
        stateChanged();
      }

      // Open file and seek to the end of it.
      file = new RandomAccessFile(getFileName(url), "rw");
      file.seek(downloaded);

      stream = connection.getInputStream();
      while (status == DOWNLOADING) {
        /*
         * Size buffer according to how much of the file is left to download.
         */
        byte buffer[];
        if (size - downloaded > MAX_BUFFER_SIZE) {
          buffer = new byte[MAX_BUFFER_SIZE];
        } else {
          buffer = new byte[size - downloaded];
        }

        // Read from server into buffer.
        int read = stream.read(buffer);
        if (read == -1)
          break;

        // Write buffer to file.
        file.write(buffer, 0, read);
        downloaded += read;
        stateChanged();
      }

      /*
       * Change status to complete if this point was reached because downloading
       * has finished.
       */
      if (status == DOWNLOADING) {
        status = COMPLETE;
        stateChanged();
      }
    } catch (Exception e) {
      error();
    } finally {
      // Close file.
      if (file != null) {
        try {
          file.close();
        } catch (Exception e) {
        }
      }

      // Close connection to server.
      if (stream != null) {
        try {
          stream.close();
        } catch (Exception e) {
        }
      }
    }
  }

  private void stateChanged() {
    setChanged();
    notifyObservers();
  }
}

class DownloadsTableModel extends AbstractTableModel implements Observer {
  private static final String[] columnNames = { "URL", "Size", "Progress", "Status" };

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
    case 0: // URL
      return download.getUrl();
    case 1: // Size
      int size = download.getSize();
      return (size == -1) ? "" : Integer.toString(size);
    case 2: // Progress
      return new Float(download.getProgress());
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

           