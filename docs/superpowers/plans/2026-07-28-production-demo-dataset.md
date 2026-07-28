# 生产环境演示数据集实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 生成 12 个虚构管理人、48 只虚构产品和严格 80 份可检索文件，经过备份后幂等导入 FundRAG 生产环境，并验证文件、向量、引用和权限。

**架构：** `demo-data/manifest.json` 是数据集唯一清单源；Python 脚本确定性生成 SQL、PDF、DOCX、TXT，下载并校验 6 份官方 PDF。PowerShell 入口先通过 SSH 执行生产备份和事务 SQL，再通过现有管理员 API 创建知识库、授权并限速上传文件，最后运行只读验收。

**技术栈：** Python 3、`reportlab`、`python-docx`、`pypdf`、PowerShell、PostgreSQL、Spring Boot REST API、MinIO、Kafka、PGVector、Poppler、LibreOffice。

---

## 文件结构

### 创建

- `demo-data/README.md`：数据真实性边界、生成、导入、恢复和验收说明。
- `demo-data/catalog.json`：12 个管理人、48 只产品和 74 份自制文件的确定性内容源。
- `demo-data/official-sources.json`：6 份官方 PDF 的发布页、附件 URL、发布日期和生效状态。
- `demo-data/manifest.json`：生成后的 80 份文件清单、SHA-256、知识库、角色和检索关键词。
- `demo-data/sql/seed-business-data.sql`：只处理 `DEMO-` 命名空间的事务化幂等 SQL。
- `demo-data/scripts/dataset.py`：读取、校验目录并生成 manifest 与 SQL。
- `demo-data/scripts/document_builder.py`：统一 PDF、DOCX、TXT 排版和免责声明。
- `demo-data/scripts/generate_documents.py`：按 catalog 生成 74 份自制文件。
- `demo-data/scripts/download_official_documents.py`：下载并校验 6 份官方 PDF。
- `demo-data/scripts/validate_dataset.py`：校验数量、格式、文本、关系、哈希和危险内容。
- `demo-data/scripts/production_api.py`：使用标准库实现登录、知识库、角色、上传和状态轮询。
- `demo-data/scripts/import-production.ps1`：备份、SQL 导入和 API 上传入口。
- `demo-data/scripts/verify-production.ps1`：生产数量、状态、预览和问答验收入口。
- `demo-data/scripts/tests/test_dataset.py`：目录、分布、关系和 SQL 幂等约束测试。
- `demo-data/scripts/tests/test_documents.py`：文档格式、正文标记和可提取文本测试。
- `demo-data/scripts/tests/test_production_api.py`：API 请求、multipart、失败停止和轮询测试。
- `demo-data/documents/product/*`：48 份产品说明书。
- `demo-data/documents/manager/*`：12 份管理人尽调报告。
- `demo-data/documents/reports/*`：8 份重点产品报告。
- `demo-data/documents/internal/*`：6 份内部制度与跨库索引。
- `demo-data/documents/official/*`：6 份官方 PDF。

### 不修改

- `sql/init.sql`：避免演示数据进入所有环境的通用初始化流程。
- `smrag-core/src/main/resources/db/migration/*`：避免 Flyway 自动污染正式生产数据。
- 现有控制器、鉴权、文档解析、向量写入和问答代码。

## 任务 1：建立确定性业务目录

**文件：**
- 创建：`demo-data/catalog.json`
- 创建：`demo-data/official-sources.json`
- 创建：`demo-data/scripts/dataset.py`
- 测试：`demo-data/scripts/tests/test_dataset.py`

- [ ] **步骤 1：编写目录分布失败测试**

```python
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


class DatasetCatalogTest(unittest.TestCase):
    def test_catalog_has_required_distribution(self):
        catalog = json.loads((ROOT / "catalog.json").read_text(encoding="utf-8"))
        managers = catalog["managers"]
        products = catalog["products"]
        documents = catalog["documents"]

        self.assertEqual(12, len(managers))
        self.assertEqual(48, len(products))
        self.assertEqual(74, len(documents))
        self.assertEqual(
            {"R2": 8, "R3": 16, "R4": 16, "R5": 8},
            {
                level: sum(item["riskLevel"] == level for item in products)
                for level in ("R2", "R3", "R4", "R5")
            },
        )
        self.assertTrue(all(item["code"].startswith("DEMO-M") for item in managers))
        self.assertTrue(all(item["code"].startswith("DEMO-P") for item in products))
        self.assertEqual(
            {"product": 48, "manager": 12, "reports": 8, "internal": 6},
            {
                category: sum(item["category"] == category for item in documents)
                for category in ("product", "manager", "reports", "internal")
            },
        )

    def test_every_product_has_one_manager_and_one_primary_document(self):
        catalog = json.loads((ROOT / "catalog.json").read_text(encoding="utf-8"))
        manager_codes = {item["code"] for item in catalog["managers"]}
        primary_codes = {
            item["productCode"]
            for item in catalog["documents"]
            if item["category"] == "product"
        }
        for product in catalog["products"]:
            self.assertIn(product["managerCode"], manager_codes)
            self.assertIn(product["code"], primary_codes)


if __name__ == "__main__":
    unittest.main()
```

- [ ] **步骤 2：运行测试并确认缺少目录**

运行：

```powershell
$env:PYTHONPATH = (Resolve-Path 'demo-data/scripts').Path
& 'C:\Users\judy_\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' `
  -m unittest discover -s demo-data/scripts/tests -p 'test_dataset.py' -v
```

预期：测试因 `demo-data/catalog.json` 不存在而失败。

- [ ] **步骤 3：创建业务目录**

`catalog.json` 使用以下固定生成规则落成实际 12 个管理人和 48 只产品，不在运行时随机取值：

```python
MANAGER_NAMES = [
    "澄明私募基金管理（演示）", "远川私募基金管理（演示）",
    "衡岳私募基金管理（演示）", "青岚私募基金管理（演示）",
    "启辰私募基金管理（演示）", "观澜私募基金管理（演示）",
    "朴石私募基金管理（演示）", "云程私募基金管理（演示）",
    "知行私募基金管理（演示）", "明晟私募基金管理（演示）",
    "栖梧私募基金管理（演示）", "长桥私募基金管理（演示）",
]

STRATEGIES = [
    "股票多头", "市场中性", "CTA", "固定收益增强",
    "宏观对冲", "量化多因子", "私募股权", "创业投资",
]

RISK_LEVELS = ["R2"] * 8 + ["R3"] * 16 + ["R4"] * 16 + ["R5"] * 8
MIN_AMOUNTS = [1_000_000, 2_000_000, 3_000_000, 5_000_000]
```

每个目录对象必须写出稳定编码、名称、管理人关系、策略、风险、最低金额、演示历史指标、数据日期、文档格式、知识库和预期关键词。4 份新季度报告、2 份策略变化报告、2 份缺失材料尽调、1 份旧制度和 1 份未生效法规使用显式字段表达，不靠随机文本制造冲突。

- [ ] **步骤 4：创建官方来源清单**

`official-sources.json` 写入以下 6 个固定附件 URL：

```json
[
  {
    "id": "official-suitability",
    "title": "证券期货投资者适当性管理办法",
    "publicationPage": "https://www.csrc.gov.cn/csrc/c101939/c1045348/content.shtml",
    "attachmentUrl": "https://www.csrc.gov.cn/csrc/c101939/c1045348/1045348/files/%E9%99%84%E4%BB%B61%EF%BC%9A%E8%AF%81%E5%88%B8%E6%9C%9F%E8%B4%A7%E6%8A%95%E8%B5%84%E8%80%85%E9%80%82%E5%BD%93%E6%80%A7%E7%AE%A1%E7%90%86%E5%8A%9E%E6%B3%95.pdf",
    "effectiveStatus": "EFFECTIVE"
  },
  {
    "id": "official-private-fund-interim",
    "title": "私募投资基金监督管理暂行办法",
    "publicationPage": "https://www.csrc.gov.cn/csrc/c100028/c1002081/content.shtml",
    "attachmentUrl": "https://www.csrc.gov.cn/qinghai/c105502/c1362737/1362737/files/P020141226569647963233.pdf",
    "effectiveStatus": "EFFECTIVE"
  },
  {
    "id": "official-registration",
    "title": "私募投资基金登记备案办法",
    "publicationPage": "https://www.amac.org.cn/xwfb/xhyw/202302/t20230224_15636.html",
    "attachmentUrl": "https://www.amac.org.cn/xwfb/xhyw/202302/P020231126355261616824.pdf",
    "effectiveStatus": "EFFECTIVE"
  },
  {
    "id": "official-manager-guide-1",
    "title": "私募基金管理人登记指引第1号——基本经营要求",
    "publicationPage": "https://www.amac.org.cn/xwfb/xhyw/202302/t20230224_15636.html",
    "attachmentUrl": "https://www.amac.org.cn/xwfb/xhyw/202302/P020231126355263137572.pdf",
    "effectiveStatus": "EFFECTIVE"
  },
  {
    "id": "official-disclosure-233",
    "title": "私募投资基金信息披露监督管理办法",
    "publicationPage": "https://www.csrc.gov.cn/csrc/c100028/c7616846/content.shtml",
    "attachmentUrl": "https://www.csrc.gov.cn/csrc/c101953/c7616837/7616837/files/%E9%99%84%E4%BB%B61%EF%BC%9A%E7%A7%81%E5%8B%9F%E6%8A%95%E8%B5%84%E5%9F%BA%E9%87%91%E4%BF%A1%E6%81%AF%E6%8A%AB%E9%9C%B2%E7%9B%91%E7%9D%A3%E7%AE%A1%E7%90%86%E5%8A%9E%E6%B3%95.pdf",
    "effectiveStatus": "PUBLISHED_NOT_EFFECTIVE",
    "effectiveDate": "2026-09-01"
  },
  {
    "id": "official-manager-change-guide-3",
    "title": "私募投资基金备案指引第3号——私募投资基金变更管理人（2025年修订）",
    "publicationPage": "https://www.amac.org.cn/fwdt/wyb/jgdjhcpbeian/smjjglrdjhcpba/fwtz/202510/t20251024_26990.html",
    "attachmentUrl": "https://www.amac.org.cn/xwfb/xhyw/202510/P020251024606683240072.pdf",
    "effectiveStatus": "EFFECTIVE"
  }
]
```

- [ ] **步骤 5：运行目录测试**

运行前述 `unittest` 命令。

预期：2 个测试通过。

- [ ] **步骤 6：提交目录**

```bash
git add demo-data/catalog.json demo-data/official-sources.json demo-data/scripts/dataset.py demo-data/scripts/tests/test_dataset.py
git commit -m "feat: 添加演示业务数据目录"
```

## 任务 2：生成幂等业务 SQL

**文件：**
- 修改：`demo-data/scripts/dataset.py`
- 创建：`demo-data/sql/seed-business-data.sql`
- 修改：`demo-data/scripts/tests/test_dataset.py`

- [ ] **步骤 1：编写 SQL 安全约束失败测试**

```python
def test_generated_sql_is_namespaced_and_transactional(self):
    sql = (ROOT / "sql" / "seed-business-data.sql").read_text(encoding="utf-8")
    self.assertTrue(sql.startswith("BEGIN;"))
    self.assertTrue(sql.rstrip().endswith("COMMIT;"))
    self.assertIn("DEMO-M", sql)
    self.assertIn("DEMO-P", sql)
    self.assertIn("SELECT id FROM fund_manager WHERE manager_code =", sql)
    self.assertNotIn("TRUNCATE", sql.upper())
    self.assertNotIn("DELETE FROM FUND_", sql.upper())
```

- [ ] **步骤 2：运行测试并确认 SQL 尚不存在**

预期：`seed-business-data.sql` 不存在。

- [ ] **步骤 3：实现 SQL 生成**

`dataset.py` 对每个管理人和产品使用 `INSERT ... SELECT ... WHERE NOT EXISTS`，随后只按稳定演示编码执行 `UPDATE`。产品的 `manager_id` 必须由 `manager_code` 子查询取得：

```sql
BEGIN;

INSERT INTO fund_manager (
    manager_code, manager_name, company_name, register_time,
    assets_scale, qualification
)
SELECT
    'DEMO-M001',
    '澄明私募基金管理（演示）',
    '澄明私募基金管理有限公司（演示）',
    TIMESTAMP '2018-03-12 00:00:00',
    3200000000.00,
    '演示资质：已建立投研、风控和运营隔离机制'
WHERE NOT EXISTS (
    SELECT 1 FROM fund_manager WHERE manager_code = 'DEMO-M001'
);

UPDATE fund_manager
SET manager_name = '澄明私募基金管理（演示）',
    company_name = '澄明私募基金管理有限公司（演示）',
    assets_scale = 3200000000.00,
    qualification = '演示资质：已建立投研、风控和运营隔离机制'
WHERE manager_code = 'DEMO-M001';

INSERT INTO fund_product (
    product_code, product_name, manager_id, risk_level, return_rate,
    investment_strategy, min_amount, status
)
SELECT
    'DEMO-P001',
    '澄明稳健增强一号（演示）',
    (SELECT id FROM fund_manager WHERE manager_code = 'DEMO-M001'),
    'R2',
    5.2600,
    '固定收益增强',
    1000000.00,
    'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1 FROM fund_product WHERE product_code = 'DEMO-P001'
);

COMMIT;
```

生成器对 12 个管理人和 48 只产品展开相同语义，并用单引号转义函数处理中文文本。

- [ ] **步骤 4：重新生成 SQL 并运行测试**

运行：

```powershell
& 'C:\Users\judy_\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' `
  demo-data/scripts/dataset.py --write-sql
$env:PYTHONPATH = (Resolve-Path 'demo-data/scripts').Path
& 'C:\Users\judy_\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' `
  -m unittest discover -s demo-data/scripts/tests -p 'test_dataset.py' -v
```

预期：测试全部通过，SQL 包含 12 个 `DEMO-M` 和 48 个 `DEMO-P` 稳定编码。

- [ ] **步骤 5：提交 SQL 生成**

```bash
git add demo-data/scripts/dataset.py demo-data/scripts/tests/test_dataset.py demo-data/sql/seed-business-data.sql
git commit -m "feat: 生成幂等演示业务数据"
```

## 任务 3：生成 74 份自制文档

**文件：**
- 创建：`demo-data/scripts/document_builder.py`
- 创建：`demo-data/scripts/generate_documents.py`
- 创建：`demo-data/scripts/tests/test_documents.py`
- 创建：`demo-data/documents/product/*`
- 创建：`demo-data/documents/manager/*`
- 创建：`demo-data/documents/reports/*`
- 创建：`demo-data/documents/internal/*`

- [ ] **步骤 1：编写文档数量和真实性失败测试**

```python
import json
import unittest
from pathlib import Path

from pypdf import PdfReader
from docx import Document

ROOT = Path(__file__).resolve().parents[2]
DISCLAIMER = "演示材料｜内容纯属虚构｜不构成投资建议"


def extract_text(path: Path) -> str:
    if path.suffix.lower() == ".pdf":
        return "\n".join(page.extract_text() or "" for page in PdfReader(path).pages)
    if path.suffix.lower() == ".docx":
        document = Document(path)
        return "\n".join(paragraph.text for paragraph in document.paragraphs)
    return path.read_text(encoding="utf-8")


class GeneratedDocumentsTest(unittest.TestCase):
    def test_generated_documents_match_catalog(self):
        catalog = json.loads((ROOT / "catalog.json").read_text(encoding="utf-8"))
        expected = {item["relativePath"] for item in catalog["documents"]}
        actual = {
            path.relative_to(ROOT).as_posix()
            for path in (ROOT / "documents").rglob("*")
            if path.is_file() and "official" not in path.parts
        }
        self.assertEqual(expected, actual)
        self.assertEqual(74, len(actual))

    def test_every_generated_document_is_labeled_and_searchable(self):
        catalog = json.loads((ROOT / "catalog.json").read_text(encoding="utf-8"))
        for item in catalog["documents"]:
            text = extract_text(ROOT / item["relativePath"])
            self.assertIn(DISCLAIMER, text)
            self.assertIn(item["stableCode"], text)
            self.assertGreaterEqual(len(text), 500)


if __name__ == "__main__":
    unittest.main()
```

- [ ] **步骤 2：运行测试并确认文件尚未生成**

预期：74 份文件集合为空，测试失败。

- [ ] **步骤 3：实现统一文档构建器**

选择 `standard_business_brief` 预设和 `memo_masthead` 首屏模式，编码以下固定样式：

```python
STYLE = {
    "page_size": "LETTER",
    "margins_in": 1.0,
    "font": "Microsoft YaHei",
    "body_size_pt": 11,
    "body_after_pt": 6,
    "body_line_spacing": 1.10,
    "h1_size_pt": 16,
    "h1_color": "2E74B5",
    "h1_before_pt": 16,
    "h1_after_pt": 8,
    "h2_size_pt": 13,
    "h2_color": "2E74B5",
    "h2_before_pt": 12,
    "h2_after_pt": 6,
    "table_width_dxa": 9360,
    "table_indent_dxa": 120,
    "cell_margins_dxa": {"top": 80, "bottom": 80, "start": 120, "end": 120},
}
```

PDF 使用 ReportLab 注册可用中文字体并生成页码；DOCX 使用 `python-docx`、显式字体、段落间距、页边距和固定表格宽度；TXT 使用 UTF-8。三种格式共用同一内容模型，第一页/首段必须包含免责声明、稳定编码、数据日期和版本状态。

- [ ] **步骤 4：实现分类内容模板**

每份产品说明书包含：产品编码、管理人编码、策略、风险等级、最低金额、演示历史指标、投资范围、风险因素、流动性、适当性、材料日期和免责声明。

每份管理人尽调报告包含：管理人编码、组织架构、投研流程、风险控制、运营保障、利益冲突、材料清单、待核验事项和结论边界。

重点产品报告包含：对应产品编码、报告期、策略暴露变化、风险观察、与主说明书的版本关系。

内部文件固定为：产品尽调清单、投委会审议流程、产品风险分级制度、材料时效管理制度、引用与证据规范、跨知识库研究索引。

- [ ] **步骤 5：生成文件并运行结构测试**

运行：

```powershell
& 'C:\Users\judy_\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' `
  demo-data/scripts/generate_documents.py
$env:PYTHONPATH = (Resolve-Path 'demo-data/scripts').Path
& 'C:\Users\judy_\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' `
  -m unittest discover -s demo-data/scripts/tests -p 'test_documents.py' -v
```

预期：74 份文件存在，免责声明、稳定编码和最小文本长度全部通过。

- [ ] **步骤 6：提交自制文档生成**

```bash
git add demo-data/scripts/document_builder.py demo-data/scripts/generate_documents.py demo-data/scripts/tests/test_documents.py demo-data/documents
git commit -m "feat: 生成演示尽调文档"
```

## 任务 4：下载并校验 6 份官方 PDF

**文件：**
- 创建：`demo-data/scripts/download_official_documents.py`
- 修改：`demo-data/scripts/tests/test_documents.py`
- 创建：`demo-data/documents/official/*`

- [ ] **步骤 1：编写官方文件失败测试**

```python
def test_official_documents_are_real_pdfs(self):
    sources = json.loads((ROOT / "official-sources.json").read_text(encoding="utf-8"))
    official_dir = ROOT / "documents" / "official"
    files = sorted(official_dir.glob("*.pdf"))
    self.assertEqual(6, len(files))
    self.assertEqual(6, len(sources))
    for path in files:
        self.assertEqual(b"%PDF-", path.read_bytes()[:5])
        reader = PdfReader(path)
        text = "\n".join(page.extract_text() or "" for page in reader.pages)
        self.assertGreaterEqual(len(text), 300)
```

- [ ] **步骤 2：运行测试并确认官方目录为空**

预期：文件数为 0，测试失败。

- [ ] **步骤 3：实现安全下载**

下载器仅允许 `csrc.gov.cn`、`amac.org.cn` 子域，使用固定 User-Agent、30 秒超时、最多 3 次重试和 25 MB 上限。响应必须以 `%PDF-` 开头，`pypdf` 能打开且可提取至少 300 个字符后才原子移动到正式目录。文件名采用 `OFFICIAL-01-适当性管理办法.pdf` 至 `OFFICIAL-06-变更管理人指引.pdf`。

- [ ] **步骤 4：下载并运行测试**

运行：

```powershell
& 'C:\Users\judy_\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' `
  demo-data/scripts/download_official_documents.py
$env:PYTHONPATH = (Resolve-Path 'demo-data/scripts').Path
& 'C:\Users\judy_\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' `
  -m unittest discover -s demo-data/scripts/tests -p 'test_documents.py' -v
```

预期：6 份真实 PDF 通过签名、页结构和文本检查；信息披露办法 manifest 状态为 `PUBLISHED_NOT_EFFECTIVE`，生效日为 `2026-09-01`。

- [ ] **步骤 5：提交官方材料**

```bash
git add demo-data/scripts/download_official_documents.py demo-data/scripts/tests/test_documents.py demo-data/documents/official
git commit -m "feat: 添加官方监管材料"
```

## 任务 5：生成 manifest 并完成文档视觉验收

**文件：**
- 创建：`demo-data/manifest.json`
- 创建：`demo-data/scripts/validate_dataset.py`
- 创建：`demo-data/README.md`

- [ ] **步骤 1：实现 manifest**

对每份文件记录以下完整字段：

```json
{
  "id": "product-DEMO-P001",
  "stableCode": "DEMO-P001",
  "relativePath": "documents/product/DEMO-P001-澄明稳健增强一号（演示）.pdf",
  "knowledgeBaseCode": "demo-product",
  "roleCodes": ["DEMO_RESEARCHER"],
  "sourceType": "SYNTHETIC_DEMO",
  "dataDate": "2026-06-30",
  "versionStatus": "CURRENT",
  "expectedKeywords": ["DEMO-P001", "固定收益增强", "R2"]
}
```

生成器在写出该对象时增加 `sha256` 字段并填入真实的 64 位小写十六进制值。manifest 顶层包含 `datasetVersion`、生成时间、12/48/80 计数和 4 个知识库定义。

- [ ] **步骤 2：实现机器校验**

`validate_dataset.py` 必须验证：

- manifest 恰有 80 条且路径唯一。
- 文件格式分布为 PDF 43、DOCX 28、TXT 9。
- SHA-256 与文件一致。
- 74 份自制文件均含免责声明，6 份官方文件不篡改。
- 不包含邮箱、手机号、身份证号和真实客户字段。
- 每个产品有主说明书，每个管理人有尽调报告。
- 知识库分布为 `demo-product` 56、`demo-manager` 12、`demo-compliance` 11、`demo-research` 1。

- [ ] **步骤 3：渲染全部 DOCX**

逐个运行：

```powershell
$docxPath = (Resolve-Path 'demo-data/documents/product/DEMO-P002-远川均衡精选一号（演示）.docx').Path
$qaDir = Join-Path $env:TEMP 'fundrag-demo-docx-qa-DEMO-P002'
& 'C:\Users\judy_\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' `
  'C:\Users\judy_\.codex\plugins\cache\openai-primary-runtime\documents\26.727.11326\skills\documents\render_docx.py' `
  $docxPath --output_dir $qaDir
```

批处理按 manifest 将 `$docxPath` 和 `$qaDir` 更新为当前文件的绝对路径。每份 DOCX 的全部 `page-N.png` 都要检查：中文字体、标题、表格、页码、分页、免责声明无裁切或重叠。

- [ ] **步骤 4：渲染全部 PDF**

逐个运行：

```powershell
$pdfPath = (Resolve-Path 'demo-data/documents/product/DEMO-P001-澄明稳健增强一号（演示）.pdf').Path
$qaPrefix = Join-Path $env:TEMP 'fundrag-demo-pdf-qa-DEMO-P001'
pdftoppm -png $pdfPath $qaPrefix
```

批处理按 manifest 更新 `$pdfPath` 和 `$qaPrefix`。检查所有页面；官方 PDF 只核验可读性和完整性，不改动版式。

- [ ] **步骤 5：运行最终本地校验**

```powershell
& 'C:\Users\judy_\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' `
  demo-data/scripts/dataset.py --write-manifest
& 'C:\Users\judy_\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' `
  demo-data/scripts/validate_dataset.py
$env:PYTHONPATH = (Resolve-Path 'demo-data/scripts').Path
& 'C:\Users\judy_\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' `
  -m unittest discover -s demo-data/scripts/tests -v
```

预期：输出 `managers=12 products=48 documents=80 pdf=43 docx=28 txt=9`，所有测试通过。

- [ ] **步骤 6：提交完整本地数据包**

```bash
git add demo-data/manifest.json demo-data/scripts/validate_dataset.py demo-data/README.md demo-data/documents
git commit -m "feat: 完成生产演示数据包"
```

## 任务 6：实现生产 API 客户端

**文件：**
- 创建：`demo-data/scripts/production_api.py`
- 创建：`demo-data/scripts/tests/test_production_api.py`

- [ ] **步骤 1：编写登录和 multipart 失败测试**

```python
import io
import json
import unittest
from pathlib import Path
from unittest.mock import patch

from production_api import ApiClient


class FakeResponse:
    def __init__(self, payload):
        self.payload = json.dumps(payload).encode("utf-8")

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb):
        return False

    def read(self):
        return self.payload


class ProductionApiTest(unittest.TestCase):
    @patch("urllib.request.urlopen")
    def test_login_uses_api_envelope_and_bearer_token(self, urlopen):
        urlopen.return_value = FakeResponse({
            "code": 200,
            "message": "success",
            "data": {"accessToken": "token-123", "tokenType": "Bearer"},
        })
        client = ApiClient("https://fundrag.example")
        client.login("admin", "secret")
        self.assertEqual("Bearer token-123", client.authorization)

    def test_multipart_contains_file_and_required_fields(self):
        client = ApiClient("https://fundrag.example")
        body, content_type = client.multipart_body(
            Path("sample.pdf"),
            b"%PDF-demo",
            {"knowledgeBaseCode": "demo-product", "roles": "DEMO_RESEARCHER"},
        )
        self.assertIn("multipart/form-data; boundary=", content_type)
        self.assertIn(b'name="file"; filename="sample.pdf"', body)
        self.assertIn(b'name="knowledgeBaseCode"', body)
        self.assertIn(b"demo-product", body)


if __name__ == "__main__":
    unittest.main()
```

测试命令通过 `PYTHONPATH=demo-data/scripts` 引入 `production_api.py`，测试代码不动态修改 `sys.path`。

- [ ] **步骤 2：运行测试并确认客户端不存在**

预期：导入失败。

- [ ] **步骤 3：实现 API 客户端**

客户端使用 `urllib.request`，实现：

- `login(username, password)` → `POST /api/auth/login`
- `list_roles()` → `GET /api/roles`
- `create_role(payload)` → `POST /api/roles`
- `list_knowledge_bases()` → `GET /api/admin/knowledge-bases`
- `create_knowledge_base(payload)` → `POST /api/admin/knowledge-bases`
- `list_documents(code)` → `GET /api/knowledge/list?knowledgeBaseCode=...`
- `upload_document(path, code, roles)` → `POST /api/knowledge/upload`
- `download_document(id)` → `GET /api/knowledge/{id}/file`
- `ask(payload)` → `POST /api/chat/ask`

所有方法校验 HTTP 状态和 `Result.code == 200`。口令只从 `FUNDRAG_DEMO_ADMIN_PASSWORD` 环境变量读取；日志不得打印 Authorization、口令或刷新令牌。

- [ ] **步骤 4：实现上传轮询**

每批最多 10 份、并发上限 2。上传后每 10 秒轮询目标知识库，最长等待 15 分钟；出现 `FAILED` 立即记录错误并停止下一批，未在超时内变为 `COMPLETED` 视为失败。

- [ ] **步骤 5：运行测试**

运行：

```powershell
$env:PYTHONPATH = (Resolve-Path 'demo-data/scripts').Path
& 'C:\Users\judy_\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' `
  -m unittest discover -s demo-data/scripts/tests -p 'test_production_api.py' -v
```

预期：登录、multipart、错误包络和轮询测试通过。

- [ ] **步骤 6：提交 API 客户端**

```bash
git add demo-data/scripts/production_api.py demo-data/scripts/tests/test_production_api.py
git commit -m "feat: 添加演示数据生产导入客户端"
```

## 任务 7：实现生产备份、导入和只读验证入口

**文件：**
- 创建：`demo-data/scripts/import-production.ps1`
- 创建：`demo-data/scripts/verify-production.ps1`
- 修改：`demo-data/README.md`

- [ ] **步骤 1：实现生产参数和前置检查**

`import-production.ps1` 定义必填参数：

```powershell
param(
    [Parameter(Mandatory = $true)][string]$BaseUrl,
    [Parameter(Mandatory = $true)][string]$SshTarget,
    [Parameter(Mandatory = $true)][string]$RemoteProjectPath,
    [Parameter(Mandatory = $true)][string]$AdminUsername,
    [switch]$Apply
)

$ErrorActionPreference = 'Stop'
if (-not $env:FUNDRAG_DEMO_ADMIN_PASSWORD) {
    throw 'FUNDRAG_DEMO_ADMIN_PASSWORD is required'
}
if (-not (Test-Path -LiteralPath "$PSScriptRoot\..\manifest.json")) {
    throw 'manifest.json is missing'
}
```

不带 `-Apply` 时只执行健康检查、登录、角色/知识库冲突检查、SSH 连通检查和待导入摘要，不写生产。

- [ ] **步骤 2：实现备份与事务 SQL**

带 `-Apply` 时先通过 SSH 在远端项目目录执行：

```bash
./scripts/backup.sh
```

只有备份命令退出码为 0 且输出目录包含完成标记后，才把 `seed-business-data.sql` 通过标准输入交给：

```bash
docker compose --env-file .env.prod -f compose.prod.yml exec -T postgres \
  psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"
```

不得把数据库密码拼进命令行。

- [ ] **步骤 3：实现角色、知识库和文件导入**

若 `DEMO_RESEARCHER` 不存在，通过 `/api/roles` 创建。4 个知识库按稳定 code 幂等创建；若 code 已存在但名称不含“演示”，立即停止。上传时根据 manifest 分组、按 SHA-256 和文件名跳过已完成文件，在 `demo-data/import-reports/` 中按 UTC 时间生成 JSON 批次报告。

- [ ] **步骤 4：实现生产只读验收**

`verify-production.ps1` 调用 API 和远端只读 SQL，断言：

- 管理人 12、产品 48。
- 4 个演示知识库存在。
- 80 份文件为 `COMPLETED` 且 `chunk_count > 0`。
- `rag_vector_store` 中 80 个文档均至少有 1 条当前版本向量。
- 下载 1 份 PDF、1 份 DOCX、1 份 TXT 后 SHA-256 与 manifest 一致。
- 5 个授权问答返回正确知识库引用。
- 未授权测试用户无法下载演示文件且回答不引用演示知识库。

- [ ] **步骤 5：运行 PowerShell 语法与 dry-run**

```powershell
$null = [System.Management.Automation.Language.Parser]::ParseFile(
  (Resolve-Path 'demo-data/scripts/import-production.ps1'),
  [ref]$null,
  [ref]$null
)
$null = [System.Management.Automation.Language.Parser]::ParseFile(
  (Resolve-Path 'demo-data/scripts/verify-production.ps1'),
  [ref]$null,
  [ref]$null
)
```

提供真实生产参数运行但不加 `-Apply`。

预期：只输出目标、冲突检查、待导入 12/48/80 和备份要求；生产数量不变化。

- [ ] **步骤 6：提交生产入口**

```bash
git add demo-data/scripts/import-production.ps1 demo-data/scripts/verify-production.ps1 demo-data/README.md
git commit -m "feat: 添加演示数据生产导入流程"
```

## 任务 8：执行生产导入并验收

**文件：**
- 创建：`demo-data/import-reports/` 下的 UTC 时间命名 JSON 报告

- [ ] **步骤 1：记录生产目标**

从用户提供的生产 HTTPS 地址、SSH 目标和远端项目路径构造命令。凭据只放入当前进程环境，不写入仓库、终端输出或导入报告。

- [ ] **步骤 2：运行 dry-run**

```powershell
.\demo-data\scripts\import-production.ps1 `
  -BaseUrl $env:FUNDRAG_DEMO_BASE_URL `
  -SshTarget $env:FUNDRAG_DEMO_SSH_TARGET `
  -RemoteProjectPath $env:FUNDRAG_DEMO_REMOTE_PATH `
  -AdminUsername $env:FUNDRAG_DEMO_ADMIN_USERNAME
```

预期：健康、认证、SSH、业务编码和知识库冲突检查通过，生产未写入。

- [ ] **步骤 3：执行备份和导入**

```powershell
.\demo-data\scripts\import-production.ps1 `
  -BaseUrl $env:FUNDRAG_DEMO_BASE_URL `
  -SshTarget $env:FUNDRAG_DEMO_SSH_TARGET `
  -RemoteProjectPath $env:FUNDRAG_DEMO_REMOTE_PATH `
  -AdminUsername $env:FUNDRAG_DEMO_ADMIN_USERNAME `
  -Apply
```

预期顺序：备份完成 → SQL 事务提交 → 角色/知识库就绪 → 8 个上传批次完成 → 80 份文档均为 `COMPLETED`。

- [ ] **步骤 4：运行验收**

```powershell
.\demo-data\scripts\verify-production.ps1 `
  -BaseUrl $env:FUNDRAG_DEMO_BASE_URL `
  -SshTarget $env:FUNDRAG_DEMO_SSH_TARGET `
  -RemoteProjectPath $env:FUNDRAG_DEMO_REMOTE_PATH `
  -AdminUsername $env:FUNDRAG_DEMO_ADMIN_USERNAME
```

预期：结构化数据、80 份文件、向量、原文件 SHA-256、授权问答和未授权隔离全部通过。

- [ ] **步骤 5：提交非敏感导入报告**

导入报告只保存批次 ID、对象计数、文件 ID、状态、耗时、知识库和 SHA-256，不保存 URL 中的私密参数、用户名、令牌、密码、服务器 IP 或 SSH 路径。

```bash
git add demo-data/import-reports
git commit -m "chore: 记录演示数据导入结果"
```

## 最终验证命令

```powershell
& 'C:\Users\judy_\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' `
  demo-data/scripts/validate_dataset.py
$env:PYTHONPATH = (Resolve-Path 'demo-data/scripts').Path
& 'C:\Users\judy_\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' `
  -m unittest discover -s demo-data/scripts/tests -v
git status --short
git log --oneline -8
```

成功标准：

- 本地数据包严格为 12 个管理人、48 只产品和 80 份文件。
- 所有 DOCX/PDF 已渲染并逐页检查，无裁切、乱码、重叠或伪格式。
- 生产导入前备份成功。
- 生产结构化数据和文件均幂等，重复运行不增加重复记录。
- 80 份生产文档全部完成解析并生成向量。
- 授权用户能检索和预览，未授权用户不能读取或被引用。
- 导入报告不包含任何凭据或敏感连接信息。
