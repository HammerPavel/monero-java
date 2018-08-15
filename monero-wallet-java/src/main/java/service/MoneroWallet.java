package service;

import java.net.URI;
import java.util.Collection;
import java.util.List;

import model.MoneroIntegratedAddress;
import model.MoneroKeyImage;
import model.MoneroTransaction;
import model.MoneroUri;

public interface MoneroWallet {
  
  /**
   * Returns the wallet's current block height.
   * 
   * @return int is the current block height of the wallet
   */
  public int getHeight();
  
  /**
   * Get the wallet's mnemonic seed.
   * 
   * @return String is the wallet's mnemonic seed
   */
  public String getMnemonicSeed();

  /**
   * Get the wallet's view key.
   * 
   * @return String is the wallet's view key
   */
  public String getViewKey();
  
  /**
   * Create a new account with an optional label.
   * 
   * @param label specifies the label for the account (optional)
   * @return MoneroAccount is the created account
   */
  public MoneroAccount createAccount(String label);
  
  /**
   * Get all accounts for a wallet.
   * 
   * @return List<MoneroAccount> are all accounts for the wallet
   */
  public List<MoneroAccount> getAccounts();
  
  /**
   * Get all accounts for a wallet filtered by a tag.
   * 
   * @param tag is the tag for filtering accounts
   * @return List<MoneroAccount> are all accounts for the wallet with the given tag
   */
  public List<MoneroAccount> getAccounts(String tag);
  
  /**
   * Returns all wallet transactions, each containing payments, outputs, and other metadata depending on the transaction type.
   * 
   * @return List<MoneroTransaction> are all of the wallet's transactions
   */
  public List<MoneroTransaction> getAllTransactions();

  /**
   * Returns all wallet transactions specified, each containing payments, outputs, and other metadata depending on the transaction type.
   * 
   * @param getIncoming specifies if incoming transactions should be retrieved
   * @param getOutgoing specifies if outgoing transactions should be retrieved
   * @param getPending specifies if pending transactions should be retrieved
   * @param getFailed specifies if failed transactions should be retrieved
   * @param getMemPool specifies if mempool transactions should be retrieved
   * @param paymentIds allows transactions with specific transaction ids to be retrieved (optional)
   * @param minHeight allows transactions with a mininum block height to be retrieved (optional)
   * @param maxHeight allows transactions with a maximum block height to be retrieved (optional)
   * @param accountIdx index of the account to query for transactions (optional)
   * @param subAddressIndices subaddress indices to query for transactions (optional)
   * @param txIds are transaction ids to query (optional)
   * @return List<MoneroTransaction> are the retrieved transactions
   */
  public List<MoneroTransaction> getTransactions(boolean getIncoming, boolean getOutgoing, boolean getPending, boolean getFailed, boolean getMemPool, Collection<String> paymentIds, Integer minHeight, Integer maxHeight, Integer accountIdx, Collection<Integer> subAddressIndices, Collection<Integer> txIds);
  
  /**
   * Send all dust outputs back to the wallet to make them easier to spend and mix.
   * 
   * @return List<MoneroTransaction> are the resulting transactions from sweeping dust
   */
  public List<MoneroTransaction> sweepDust();
  
  /**
   * Set arbitrary string notes for transactions.
   * 
   * @param txIds identify the transactions to get notes for
   * @param txNotes are the notes to set for transactions
   */
  public void setTxNotes(List<String> txIds, List<String> txNotes);
  
  /**
   * Get arbitrary string notes for transactions.
   * 
   * @param txIds identify the transactions to get notes for
   */
  public void getTxNotes(List<String> txIds);

  public List<MoneroKeyImage> getKeyImages();
  
  public void importKeyImages(List<MoneroKeyImage> keyImages);
  
  // --------------------------- SERVICE UTILITIES ----------------------------
  
  /**
   * Get a list of available languages for wallet seeds.
   * 
   * @return List<String> is a list of available languages
   */
  public List<String> getLanguages();
  
  /**
   * Create a new wallet.
   * 
   * @param filename is the name of the wallet file to create
   * @param password is the wallet password
   * @param language is the wallet language
   */
  public void createWallet(String filename, String password, String language);
  
  /**
   * Open a wallet.
   * 
   * @param filename is the name of the wallet file to open
   * @param password is the wallet password
   */
  public void openWallet(String filename, String password);
  
  /**
   * Sign a string.
   * 
   * @param data is the string to sign
   * @return String is the signature
   */
  public String sign(String data);
  
  /**
   * Verify a signature on a string.
   * 
   * @param data is the signed string
   * @param address is the signing address
   * @param signature is the signature
   * @return true if the signature is good, false otherwise
   */
  public boolean verify(String data, String address, String signature);
  
  /**
   * Convert a MoneroUri to a standard URI.
   * 
   * @param moneroUri is the MoneroUri to convert to a standard URI
   * @return URI is the MoneroUri converted to a standard URI
   */
  public URI toUri(MoneroUri moneroUri);

  /**
   * Convert a standard URI to a Monero URI.
   * 
   * @param uri is the standard URI to convert
   * @return MoneroUri is the URI converted to a Monero URI
   */
  public MoneroUri toMoneroUri(URI uri);
  
  /**
   * Decodes an integrated address into its standard address and payment id components.
   * 
   * @param integratedAddress is a string representation of the integrated address
   * @return MoneroIntegratedAddress contains the integrated address, standard address, and payment id
   */
  public MoneroIntegratedAddress decodeIntegratedAddress(String integratedAddress);
  
  /**
   * Save the current state of the blockchain.
   */
  public void saveBlockchain();
  
  /**
   * Rescan the blockchain.
   */
  public void rescanBlockchain();
  
  /**
   * Rescan the blockchain for spent outputs.
   */
  public void rescanSpent();

  /**
   * Stop the wallet.
   */
  public void stopWallet();
}
