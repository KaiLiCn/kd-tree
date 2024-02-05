package edu.wlu.cs.levy.cg;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

/**
 * KDTree is a class supporting KD-tree insertion, deletion, equality search,
 * range search, and nearest neighbor(s) using double-precision floating-point
 * keys. Splitting dimension is chosen naively, by depth modulo K. Semantics are
 * as follows:
 * 
 * <UL>
 * <LI>Two different keys containing identical numbers should retrieve the same
 * value from a given KD-tree. Therefore keys are cloned when a node is
 * inserted. <BR>
 * <BR>
 * <LI>As with Hashtables, values inserted into a KD-tree are <I>not</I> cloned.
 * Modifying a value between insertion and retrieval will therefore modify the
 * value stored in the tree.
 *</UL>
 * 
 * Implements the Nearest Neighbor algorithm (Table 6.4) of
 * 
 * <PRE>
 * &#064;techreport{AndrewMooreNearestNeighbor,
 * author = {Andrew Moore},
 * title = {An introductory tutorial on kd-trees},
 * institution = {Robotics Institute, Carnegie Mellon University},
 * year = {1991},
 * number = {Technical Report No. 209, Computer Laboratory,
 * University of Cambridge},
 * address = {Pittsburgh, PA}
 * }
 * </PRE>
 * 
 * 
 * @author Simon Levy, Bjoern Heckel
 * @version %I%, %G%
 * @since JDK1.2
 */
public class KDTree implements Serializable {
	// number of milliseconds
	final long m_timeout = 0;

	// K = number of dimensions
	final private int m_K;

	// root of KD-tree
	private KDNode<Object> m_root;

	// count of nodes
	private int m_count;

	/**
	 * Creates a KD-tree with specified number of dimensions.
	 * 
	 * @param k
	 *            number of dimensions
	 */
	public KDTree(int k) {
		m_K = k;
		m_root = null;
	}

	/**
	 * Insert a node in a KD-tree. Uses algorithm translated from 352.ins.c of
	 * 
	 * <PRE>
	 * &#064;Book{GonnetBaezaYates1991,
	 * author = {G.H. Gonnet and R. Baeza-Yates},
	 * title = {Handbook of Algorithms and Data Structures},
	 * publisher = {Addison-Wesley},
	 * year = {1991}
	 * }
	 * </PRE>
	 * 
	 * @param key
	 *            key for KD-tree node
	 * @param value
	 *            value at that key
	 * 
	 * @throws KeySizeException
	 *             if key.length mismatches K
	 * @throws KeyDuplicateException
	 *             if key already in tree
	 */
	public void insert(double[] key, Object value) throws KeySizeException,
			KeyDuplicateException {
		this.edit(key, new Editor.Inserter<Object>(value));
	}

	/**
	 * Edit a node in a KD-tree
	 * 
	 * @param key
	 *            key for KD-tree node
	 * @param editor
	 *            object to edit the value at that key
	 * 
	 * @throws KeySizeException
	 *             if key.length mismatches K
	 * @throws KeyDuplicateException
	 *             if key already in tree
	 */

	public void edit(double[] key, Editor<Object> editor) throws KeySizeException,
			KeyDuplicateException {

		if (key.length != m_K) {
			throw new KeySizeException();
		}

		synchronized (this) {
			// the first insert has to be synchronized
			if (null == m_root) {
				m_root = KDNode.create(new HPoint(key), editor);
				m_count = m_root.deleted ? 0 : 1;
				return;
			}
		}

		m_count += KDNode.edit(new HPoint(key), editor, m_root, 0, m_K);
	}

	/**
	 * Find KD-tree node whose key is identical to key. Uses algorithm
	 * translated from 352.srch.c of Gonnet & Baeza-Yates.
	 * 
	 * @param key
	 *            key for KD-tree node
	 * 
	 * @return object at key, or null if not found
	 * 
	 * @throws KeySizeException
	 *             if key.length mismatches K
	 */
	public Object search(double[] key) throws KeySizeException {

		if (key.length != m_K) {
			throw new KeySizeException();
		}

		KDNode<Object> kd = KDNode.srch(new HPoint(key), m_root, m_K);

		return (kd == null ? null : kd.v);
	}

	public void delete(double[] key) throws KeySizeException,
			KeyMissingException {
		delete(key, false);
	}

	/**
	 * Delete a node from a KD-tree. Instead of actually deleting node and
	 * rebuilding tree, marks node as deleted. Hence, it is up to the caller to
	 * rebuild the tree as needed for efficiency.
	 * 
	 * @param key
	 *            key for KD-tree node
	 * @param optional
	 *            if false and node not found, throw an exception
	 * 
	 * @throws KeySizeException
	 *             if key.length mismatches K
	 * @throws KeyMissingException
	 *             if no node in tree has key
	 */
	public void delete(double[] key, boolean optional) throws KeySizeException,
			KeyMissingException {

		if (key.length != m_K) {
			throw new KeySizeException();
		}
		KDNode<Object> t = KDNode.srch(new HPoint(key), m_root, m_K);
		if (t == null) {
			if (optional == false) {
				throw new KeyMissingException();
			}
		} else {
			if (KDNode.del(t)) {
				m_count--;
			}
		}
	}

	/**
	 * Find KD-tree node whose key is nearest neighbor to key.
	 * 
	 * @param key
	 *            key for KD-tree node
	 * 
	 * @return object at node nearest to key, or null on failure
	 * 
	 * @throws KeySizeException
	 *             if key.length mismatches K
	 */
	public Object nearest(double[] key) throws KeySizeException {
		return nearest(key, 1, null).get(0);
	}

	/**
	 * Find KD-tree nodes whose keys are <i>n</i> nearest neighbors to key.
	 * 
	 * @param key
	 *            key for KD-tree node
	 * @param n
	 *            number of nodes to return
	 * 
	 * @return objects at nodes nearest to key, or null on failure
	 * 
	 * @throws KeySizeException
	 *             if key.length mismatches K
	 */
	public List<Object> nearest(double[] key, int n) throws KeySizeException,
			IllegalArgumentException {
		return nearest(key, n, null);
	}

	/**
	 * Find KD-tree nodes whose keys are <I>n</I> nearest neighbors to key. Uses
	 * algorithm above. Neighbors are returned in ascending order of distance to
	 * key.
	 * 
	 * @param key
	 *            key for KD-tree node
	 * @param n
	 *            how many neighbors to find
	 * @param checker
	 *            an optional object to filter matches
	 * 
	 * @return objects at node nearest to key, or null on failure
	 * 
	 * @throws KeySizeException
	 *             if key.length mismatches K
	 * @throws IllegalArgumentException
	 *             if <I>n</I> is negative or exceeds tree size
	 */
	public List<Object> nearest(double[] key, int n, Checker<Object> checker)
			throws KeySizeException, IllegalArgumentException {

		if (n <= 0) {
			return new LinkedList<Object>();
		}

		NearestNeighborList<KDNode<Object>> nnl = getNeighbors(key, n, checker);

		n = nnl.getSize();
		Stack<Object> nbrs = new Stack<Object>();

		for (int i = 0; i < n; ++i) {
			KDNode<Object> kd = nnl.removeHighest();
			nbrs.push(kd.v);
		}

		return nbrs;
	}

	/**
	 * Range search in a KD-tree. Uses algorithm translated from 352.range.c of
	 * Gonnet & Baeza-Yates.
	 * 
	 * @param lowk
	 *            lower-bounds for key
	 * @param uppk
	 *            upper-bounds for key
	 * 
	 * @return array of Objects whose keys fall in range [lowk,uppk]
	 * 
	 * @throws KeySizeException
	 *             on mismatch among lowk.length, uppk.length, or K
	 */
	public List<Object> range(double[] lowk, double[] uppk) throws KeySizeException {

		if (lowk.length != uppk.length) {
			throw new KeySizeException();
		}

		else if (lowk.length != m_K) {
			throw new KeySizeException();
		}

		else {
			List<KDNode<Object>> found = new LinkedList<KDNode<Object>>();
			KDNode.rsearch(new HPoint(lowk), new HPoint(uppk), m_root, 0, m_K,
					found);
			List<Object> o = new LinkedList<Object>();
			for (KDNode<Object> node : found) {
				o.add(node.v);
			}
			return o;
		}
	}

	public int size() {
		return m_count;
	}

	private NearestNeighborList<KDNode<Object>> getNeighbors(double[] key)
			throws KeySizeException {
		return getNeighbors(key, m_count, null);
	}

	private NearestNeighborList<KDNode<Object>> getNeighbors(double[] key, int n,
			Checker<Object> checker) throws KeySizeException {

		if (key.length != m_K) {
			throw new KeySizeException();
		}

		NearestNeighborList<KDNode<Object>> nnl = new NearestNeighborList<KDNode<Object>>(
				n);

		// initial call is with infinite hyper-rectangle and max distance
		HRect hr = HRect.infiniteHRect(key.length);
		double max_dist_sqd = Double.MAX_VALUE;
		HPoint keyp = new HPoint(key);

		if (m_count > 0) {
			long absoluteTimeout = (this.m_timeout > 0) ? (System.currentTimeMillis() + this.m_timeout)
					: 0;
			KDNode.nnbr(m_root, keyp, hr, max_dist_sqd, 0, m_K, nnl, checker,
					absoluteTimeout);
		}

		return nnl;

	}

	/**
	 * Find KD-tree nodes whose keys are within a given the given distance of a
	 * given key.
	 * 
	 * @param key
	 *            key for KD-tree node
	 * @param dist
	 *            the distance
	 * @param metric
	 *            the distiance metric
	 * @return objects at nodes with distance of key, or null on failure
	 * 
	 * @throws KeySizeException
	 *             if key.length mismatches K
	 */
	public List<Object> nearestDistance(double[] key, double dist,
			DistanceMetric metric) throws KeySizeException {

		NearestNeighborList<KDNode<Object>> nearestNeighborList = getNeighbors(key);
		int n = nearestNeighborList.getSize();
		Stack<Object> neighbors = new Stack<Object>();

		for (int i = 0; i < n; ++i) {
			KDNode<Object> kd = nearestNeighborList.removeHighest();
			if (metric.distance(kd.k.coord, key) < dist) {
				neighbors.push(kd.v);
			}
		}

		return neighbors;
	}

}
