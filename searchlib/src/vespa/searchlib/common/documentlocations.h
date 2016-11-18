// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace search {

    namespace attribute { class IAttributeVector; }
    class AttributeGuard;

namespace common {


/**
 * This class contains meta-information about document locations (positions)
 * for all documents in the index, and references to the attributes
 * containing the actual document locations.
 */
class DocumentLocations
{

private:
    std::unique_ptr<search::AttributeGuard> _vec_guard;
    const search::attribute::IAttributeVector *_vec;

public:
    DocumentLocations(DocumentLocations &&) = default;
    DocumentLocations & operator = (DocumentLocations &&) = default;
    DocumentLocations();
    virtual ~DocumentLocations();

    void setVecGuard(std::unique_ptr<search::AttributeGuard> guard);

    void setVec(const search::attribute::IAttributeVector &vec) {
        _vec = &vec;
    }

    const search::attribute::IAttributeVector *getVec() const {
        return _vec;
    }
};


}
}

