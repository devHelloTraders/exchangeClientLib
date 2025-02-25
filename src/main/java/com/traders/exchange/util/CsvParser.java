package com.traders.exchange.util;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.traders.common.model.InstrumentDTO;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.util.List;

@Component
public class CsvParser {
    public List<InstrumentDTO> parse(String csv) {
        CsvToBean<InstrumentDTO> csvToBean = new CsvToBeanBuilder<InstrumentDTO>(new StringReader(csv))
                .withType(InstrumentDTO.class)
                .withIgnoreLeadingWhiteSpace(true)
                .build();
        return csvToBean.parse();
    }
}