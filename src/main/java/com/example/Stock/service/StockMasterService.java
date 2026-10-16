package com.example.Stock.service;

import com.example.Stock.domain.StockMaster;
import com.example.Stock.repository.StockMasterRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * KIS 종목 마스터 파일(코스피/코스닥)을 받아 stock_master 테이블에 동기화하고, 종목 검색을 제공한다.
 * 마스터 파일은 인증 없이 받을 수 있는 고정 길이(CP949) 텍스트 파일이다.
 */
@Service
@RequiredArgsConstructor
public class StockMasterService {

    private static final Logger log = LoggerFactory.getLogger(StockMasterService.class);

    private static final String MASTER_URL = "https://new.real.download.dws.co.kr/common/master/%s_code.mst.zip";
    private static final Charset CP949 = Charset.forName("MS949");

    /** 각 줄 앞부분: 단축코드(9) + 표준코드(12) + 한글명(40). 그 뒤는 시장별 고정 길이 부가정보 */
    private static final int NAME_START = 21;
    private static final int NAME_END = 61;
    private static final int KOSPI_TAIL = 227;
    private static final int KOSDAQ_TAIL = 221;

    /** 검색 대상: 주권, 외국주권, 예탁증서, ETF (ETN·펀드 등은 제외) */
    private static final Set<String> SEARCHABLE_TYPES = Set.of("ST", "FS", "DR", "EF");

    private static final int SEARCH_LIMIT = 20;

    private final StockMasterRepository stockMasterRepository;
    private final RestTemplate restTemplate;

    /** 마스터 파일을 받아 DB와 동기화한다. 다운로드가 실패하면 기존 목록을 그대로 둔다. */
    @Transactional
    public int sync() {
        List<StockMaster> parsed = new ArrayList<>();
        parsed.addAll(download("kospi", "KOSPI", KOSPI_TAIL));
        parsed.addAll(download("kosdaq", "KOSDAQ", KOSDAQ_TAIL));

        Map<String, StockMaster> existing = new HashMap<>();
        for (StockMaster m : stockMasterRepository.findAll()) existing.put(m.getSymbol(), m);

        List<StockMaster> toSave = new ArrayList<>();
        for (StockMaster p : parsed) {
            StockMaster m = existing.remove(p.getSymbol());
            if (m == null) m = p;
            m.setName(p.getName());
            m.setMarket(p.getMarket());
            m.setType(p.getType());
            toSave.add(m);
        }
        stockMasterRepository.saveAll(toSave);
        // 상장폐지 등으로 목록에서 빠진 종목 정리 (다운로드 실패 시에는 parsed가 비어 있으므로 건너뜀)
        if (!parsed.isEmpty()) stockMasterRepository.deleteAll(existing.values());

        log.info("종목 마스터 동기화 완료: {}개", toSave.size());
        return toSave.size();
    }

    /** 종목명(부분 일치) 또는 종목코드(앞자리 일치)로 검색. 정확도 높은 순으로 최대 20개 */
    public List<StockMaster> search(String query) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) return List.of();

        String lower = q.toLowerCase();
        return stockMasterRepository.findBySymbolStartingWithOrNameContainingIgnoreCase(q, q).stream()
                .sorted(Comparator
                        .comparingInt((StockMaster m) -> matchRank(m, lower))
                        .thenComparing(m -> !"ST".equals(m.getType())) // 같은 순위면 일반 주식 먼저
                        .thenComparingInt(m -> m.getName().length())
                        .thenComparing(StockMaster::getName))
                .limit(SEARCH_LIMIT)
                .toList();
    }

    public String findName(String symbol) {
        return stockMasterRepository.findBySymbol(symbol)
                .map(StockMaster::getName)
                .orElse(symbol);
    }

    // 0: 이름/코드 정확히 일치, 1: 앞부분 일치, 2: 그 외 포함
    private int matchRank(StockMaster m, String lowerQuery) {
        String name = m.getName().toLowerCase();
        if (name.equals(lowerQuery) || m.getSymbol().equals(lowerQuery)) return 0;
        if (name.startsWith(lowerQuery) || m.getSymbol().startsWith(lowerQuery)) return 1;
        return 2;
    }

    private List<StockMaster> download(String file, String market, int tailLength) {
        List<StockMaster> result = new ArrayList<>();
        try {
            byte[] zip = restTemplate.getForObject(String.format(MASTER_URL, file), byte[].class);
            byte[] content = unzipFirstEntry(zip);

            int lineStart = 0;
            for (int i = 0; i <= content.length; i++) {
                if (i < content.length && content[i] != '\n') continue;
                int lineEnd = i;
                if (lineEnd > lineStart && content[lineEnd - 1] == '\r') lineEnd--;
                StockMaster m = parseLine(content, lineStart, lineEnd - lineStart, market, tailLength);
                if (m != null) result.add(m);
                lineStart = i + 1;
            }
        } catch (Exception e) {
            log.warn("종목 마스터 다운로드 실패: {} / 원인: {}", file, e.getMessage());
        }
        return result;
    }

    // 고정 길이 바이트 단위로 잘라야 한글명(CP949, 2바이트)이 섞여도 위치가 어긋나지 않는다
    private StockMaster parseLine(byte[] buf, int offset, int length, String market, int tailLength) {
        if (length < NAME_END + 2 || length - tailLength != NAME_END) return null;

        String type = new String(buf, offset + NAME_END, 2, StandardCharsets.US_ASCII);
        if (!SEARCHABLE_TYPES.contains(type)) return null;

        String symbol = new String(buf, offset, 9, StandardCharsets.US_ASCII).trim();
        String name = new String(buf, offset + NAME_START, NAME_END - NAME_START, CP949).trim();
        if (symbol.isEmpty() || name.isEmpty()) return null;

        StockMaster m = new StockMaster();
        m.setSymbol(symbol);
        m.setName(name);
        m.setMarket(market);
        m.setType(type);
        return m;
    }

    private byte[] unzipFirstEntry(byte[] zip) throws IOException {
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry = in.getNextEntry();
            if (entry == null) throw new IOException("빈 zip 파일");
            return in.readAllBytes();
        }
    }
}
