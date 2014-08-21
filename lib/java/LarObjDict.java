//字典类型，hash表实现
public final class LarObjDict extends LarObj
{
    private static final class Entry
    {
        public LarObj m_key;
        public LarObj m_value;
        
        Entry(LarObj key, LarObj value)
        {
            m_key = key;
            m_value = value;
        }
    }

    private static final Entry DUMMY = new Entry(null, null);

    private static final class LarObjDictIterator extends LarObj
    {
        private LarObjDict m_dict;
        private long m_version;
        private int m_index;
        
        LarObjDictIterator(LarObjDict dict)
        {
            m_dict = dict;
            m_version = m_dict.m_version;
            m_index = -1;
            next_index();
        }
        
        private void next_index()
        {
            for (++ m_index; m_index < m_dict.m_list.length; ++ m_index)
            {
                Entry entry = m_dict.m_list[m_index];
                if (entry != null && entry != LarObjDict.DUMMY)
                {
                    return;
                }
            }
        }
        
        public LarObj f_has_next() throws Exception
        {
            if (m_version != m_dict.m_version)
            {
                throw new Exception("dict迭代器失效");
            }
            return m_index < m_dict.m_list.length ? LarBuiltin.TRUE : LarBuiltin.FALSE;
        }

        public LarObj f_next() throws Exception
        {
            if (m_version != m_dict.m_version)
            {
                throw new Exception("dict迭代器失效");
            }
            LarObj key = m_dict.m_list[m_index].m_key;
            next_index();
            return key;
        }
    }

    private Entry[] m_list;
    private int m_count;
    private long m_version;

    LarObjDict()
    {
        m_list = new Entry[8];
        m_count = 0;
        m_version = 0;
    }

    private int get_entry_index(Entry[] list, LarObj key) throws Exception
    {
        //从hash表中查找key，如查不到则返回一个插入空位
        /*
        hash表算法简述：
        采用开放定址hash，表大小为2的幂，利用位运算代替求余数
        这种情况下探测步长为奇数即可遍历整张表，证明：
        设表大小为n，步长为i，则从任意位置开始，若经过k步第一次回到原点，则i*k被n整除
        最小的k为n/gcd(n,i)，则若要令k=n，i必须和n互质，由于n是2的幂，因此i选奇数即可
        */
        int mask = list.length - 1;
        int h = key.op_hash();
        int start = h & mask;
        int step = h | 1;
        int first_dummy_index = -1;
        for (int index = (start + step) & mask; index != start; index = (index + step) & mask)
        {
            Entry entry = list[index];
            if (entry == null)
            {
                //结束查找
                return first_dummy_index == -1 ? index : first_dummy_index;
            }
            if (entry == DUMMY)
            {
                if (first_dummy_index == -1)
                {
                    //记录第一个dummy
                    first_dummy_index = index;
                }
                continue;
            }
            if (entry.m_key.op_eq(key))
            {
                return index;
            }
        }
        //运气差，整张表都没有null
        if (first_dummy_index != -1)
        {
            return first_dummy_index;
        }
        throw new Exception("内部错误：dict被填满");
    }

    private void rehash() throws Exception
    {
        //如果可能，扩大hash表
        int size = m_list.length << 1; //新表大小为原先2倍
        if (size < 0)
        {
            //表大小已经是最大了，分情况处理
            if (m_count < m_list.length - 1)
            {
                //还没有满
                return;
            }
            throw new Exception("dict大小超限");
        }
        Entry[] new_list = new Entry[size];
        for (int i = 0; i < m_list.length; ++ i)
        {
            Entry entry = m_list[i];
            if (entry == null || entry == DUMMY)
            {
                continue;
            }
            int index = get_entry_index(new_list, entry.m_key);
            new_list[index] = entry;
        }
        m_list = new_list;
        ++ m_version;
    }

    public LarObj init_item(LarObj key, LarObj value) throws Exception
    {
        op_set_item(key, value);
        return this;
    }
    
    public String get_type_name()
    {
        return "dict";
    }

    public boolean op_bool() throws Exception
    {
        return m_count != 0;
    }

    public int op_len() throws Exception
    {
        return m_count;
    }

    public LarObj op_get_item(LarObj key) throws Exception
    {
        Entry entry = m_list[get_entry_index(m_list, key)];
        if (entry == null || entry == DUMMY)
        {
            throw new Exception("字典中找不到元素：" + key.op_str());
        }
        return entry.m_value;
    }
    public void op_set_item(LarObj key, LarObj value) throws Exception
    {
        if (m_count >= m_list.length / 2)
        {
            //装载率太高
            rehash();
        }
        int index = get_entry_index(m_list, key);
        Entry entry = m_list[index];
        if (entry == null || entry == DUMMY)
        {
            //新元素
            m_list[index] = new Entry(key, value);
            ++ m_count;
            ++ m_version;
        }
        else
        {
            entry.m_value = value;
        }
    }

    public boolean op_contain(LarObj key) throws Exception
    {
        Entry entry = m_list[get_entry_index(m_list, key)];
        return entry != null && entry != DUMMY;
    }

    public LarObj f_iterator() throws Exception
    {
        return new LarObjDictIterator(this);
    }
}
