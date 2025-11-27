import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/* Block Chain should maintain only limited block nodes to satisfy the functions
   You should not have all the blocks added to the blockchain in memory
   as it would overflow memory
*/

public class BlockChain {
    public static final int CUT_OFF_AGE = 10;

    // all information required in handling a block in blockchain
    private class BlockNode {
        public Block b;
        public BlockNode parent;
        public ArrayList<BlockNode> children;
        public int height;
        private UTXOPool uPool;  // utxo pool for making a new block on top

        public BlockNode(Block b, BlockNode parent, UTXOPool uPool) {
            this.b = b;
            this.parent = parent;
            children = new ArrayList<>();
            this.uPool = uPool;
            if (parent != null) {
                height = parent.height + 1;
                parent.children.add(this);
            } else {
                height = 1;
            }
        }

        public UTXOPool getUTXOPoolCopy() {
            return new UTXOPool(uPool);
        }
    }

    private HashMap<ByteArrayWrapper, BlockNode> blockMap; // map from block hash to BlockNode
    private BlockNode maxHeightNode;
    private TransactionPool transactionPool;

    /* create an empty blockchain with just a genesis block.
       Assume genesis block is a valid block
    */
    public BlockChain(Block genesisBlock) {
        blockMap = new HashMap<>();
        transactionPool = new TransactionPool();

        UTXOPool utxo = new UTXOPool();
        Transaction coinbase = genesisBlock.getCoinbase();
        UTXO coinbaseUTXO = new UTXO(coinbase.getHash(), 0);
        utxo.addUTXO(coinbaseUTXO, coinbase.getOutput(0));

        BlockNode genesis = new BlockNode(genesisBlock, null, utxo);
        blockMap.put(new ByteArrayWrapper(genesisBlock.getHash()), genesis);
        maxHeightNode = genesis;
    }

    /* Get the maximum height block */
    public Block getMaxHeightBlock() {
        return maxHeightNode.b;
    }

    /* Get the UTXOPool for mining a new block on top of max height block */
    public UTXOPool getMaxHeightUTXOPool() {
        return maxHeightNode.getUTXOPoolCopy();
    }

    /* Get the transaction pool to mine a new block */
    public TransactionPool getTransactionPool() {
        return transactionPool;
    }

    /* Add a block to blockchain if it is valid.
       Return true if block is successfully added
    */
    public boolean addBlock(Block b) {
        if (b.getPrevBlockHash() == null) return false;

        BlockNode parent = blockMap.get(new ByteArrayWrapper(b.getPrevBlockHash()));
        if (parent == null) return false;

        int newHeight = parent.height + 1;
        if (newHeight < maxHeightNode.height - CUT_OFF_AGE) return false;

        // Validate transactions
        TxHandler txHandler = new TxHandler(parent.getUTXOPoolCopy());
        Transaction[] txs = b.getTransactions().toArray(new Transaction[0]);
        Transaction[] validTxs = txHandler.handleTxs(txs);

        if (validTxs.length != txs.length) return false; // some invalid txs

        // Update UTXOPool
        UTXOPool newUTXOPool = txHandler.getUTXOPool();
        Transaction coinbase = b.getCoinbase();
        UTXO coinbaseUTXO = new UTXO(coinbase.getHash(), 0);
        newUTXOPool.addUTXO(coinbaseUTXO, coinbase.getOutput(0));

        // Create new BlockNode
        BlockNode newNode = new BlockNode(b, parent, newUTXOPool);
        blockMap.put(new ByteArrayWrapper(b.getHash()), newNode);

        // Update max height
        if (newNode.height > maxHeightNode.height) {
            maxHeightNode = newNode;
        }

        // Remove processed transactions from pool
        for (Transaction tx : validTxs) {
            transactionPool.removeTransaction(tx.getHash());
        }

        // Prune old blocks
        pruneBlocks();

        return true;
    }

    private void pruneBlocks() {
        int minHeight = maxHeightNode.height - CUT_OFF_AGE;
        ArrayList<ByteArrayWrapper> toRemove = new ArrayList<>();
        for (Map.Entry<ByteArrayWrapper, BlockNode> entry : blockMap.entrySet()) {
            if (entry.getValue().height < minHeight) {
                toRemove.add(entry.getKey());
            }
        }
        for (ByteArrayWrapper key : toRemove) {
            blockMap.remove(key);
        }
    }

    /* Add a transaction in transaction pool */
    public void addTransaction(Transaction tx) {
        transactionPool.addTransaction(tx);
    }
}
